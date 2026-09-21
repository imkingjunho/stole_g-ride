package com.gachiga.ride;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.util.GeoUtils;
import com.gachiga.contract.event.RideRequestCancelled;
import com.gachiga.contract.event.RideRequestCreated;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import com.gachiga.contract.route.RouteProvider;
import com.gachiga.contract.route.RouteResult;
import com.gachiga.contract.user.UserPort;
import com.gachiga.contract.user.UserStatus;
import com.gachiga.contract.user.UserSummary;
import com.gachiga.ride.dto.CreateRideRequestRequest;
import com.gachiga.ride.dto.RideRequestResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매칭 요청 생성·조회 (FR-07·FR-08).
 *
 * <p>다른 모듈의 정보는 전부 {@code contract} 의 port 로만 가져온다 — 거점은 {@link HubPort},
 * 사용자 상태는 {@link UserPort}, 단독 요금은 {@link RouteProvider}.
 * Phase 0 스텁이 붙어 있어도 그대로 동작하고, 실구현으로 바뀌어도 이 코드는 그대로다.
 */
@Slf4j
@Service
public class RideRequestService {

    /** 1인 1건 제한을 판정할 때 "진행 중" 으로 보는 상태들 */
    private static final List<RideRequestStatus> IN_PROGRESS =
            List.of(RideRequestStatus.WAITING, RideRequestStatus.MATCHED, RideRequestStatus.CONFIRMED);

    private final RideRequestRepository rideRequestRepository;
    private final RideProperties rideProperties;
    private final HubPort hubPort;
    private final UserPort userPort;
    private final RouteProvider routeProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 트랜잭션을 <b>필요한 구간에만</b> 열기 위해 직접 다룬다.
     *
     * <p>{@code create()} 에 {@code @Transactional} 을 통째로 걸면, 아직 쓸 것이 없는데도
     * DB 커넥션을 쥔 채 카카오 길찾기 응답을 기다리게 된다. Phase 1 에 실호출이 붙으면
     * 요청당 최대 수 초씩 커넥션이 묶여 동시 200명(PRD §10)에서 풀이 마른다.
     */
    private final TransactionTemplate transactionTemplate;

    public RideRequestService(
            RideRequestRepository rideRequestRepository,
            RideProperties rideProperties,
            HubPort hubPort,
            UserPort userPort,
            RouteProvider routeProvider,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.rideRequestRepository = rideRequestRepository;
        this.rideProperties = rideProperties;
        this.hubPort = hubPort;
        this.userPort = userPort;
        this.routeProvider = routeProvider;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 요청을 만들어 대기열에 올린다 (FR-08).
     *
     * <p>거절되는 경우는 네 가지다.
     *
     * <ul>
     *   <li>이용이 제한된 계정 → {@code USER_SUSPENDED}
     *   <li>진행 중인 요청이 이미 있음 → {@code ALREADY_IN_QUEUE} (1인 1건)
     *   <li>없는 거점 → {@code NOT_FOUND}
     *   <li>거점~목적지가 최소 거리 미만 → {@code REQUEST_TOO_SHORT} (E-08)
     * </ul>
     *
     * <p>단독 거리·요금은 <b>이 시점에 계산해 고정</b>한다. 절감액의 기준선이므로 나중에
     * 요금표가 바뀌어도 흔들리면 안 된다.
     */
    public RideRequestResponse create(Long userId, CreateRideRequestRequest command) {
        LocalDateTime now = LocalDateTime.now(clock);

        // ── 트랜잭션 밖: 검증과 외부 호출 ──────────────────
        validateMaxWaitMinutes(command.maxWaitMin());
        validateDetourRatio(command.maxDetourRatio());
        requireActiveUser(userId);
        requireNoRequestInProgress(userId);

        HubInfo hub = findHub(command.hubId());
        requireFarEnough(hub, command.destLat(), command.destLng());

        // 카카오 호출이 붙을 자리다. 커넥션을 쥐지 않은 채 기다린다
        RouteResult soloRoute = soloRoute(hub, command.destLat(), command.destLng());

        // ── 트랜잭션 안: 저장과 이벤트 발행만 ──────────────
        RideRequest saved = save(userId, command, hub, soloRoute, now);

        log.info(
                "매칭 요청 생성 requestId={} userId={} hubId={} 단독 {}m/{}원 추정={}",
                saved.getId(),
                userId,
                hub.id(),
                saved.getSoloDistance(),
                saved.getSoloFare(),
                saved.isEstimated());

        return RideRequestResponse.of(saved, hub, candidateCount(saved), now);
    }

    /**
     * 저장하고 생성 이벤트를 발행한다. 이 구간만 트랜잭션이다.
     *
     * <p>사전 검사를 통과했더라도 같은 사용자가 동시에 두 번 눌렀으면 여기서 유니크 제약에
     * 걸린다. 그때는 500 이 아니라 사전 검사와 같은 {@code ALREADY_IN_QUEUE} 로 돌려준다.
     */
    private RideRequest save(
            Long userId,
            CreateRideRequestRequest command,
            HubInfo hub,
            RouteResult soloRoute,
            LocalDateTime now) {

        return transactionTemplate.execute(
                status -> {
                    RideRequest request =
                            RideRequest.create(
                                    userId,
                                    hub.id(),
                                    command.destName(),
                                    command.destLat(),
                                    command.destLng(),
                                    command.departAt(),
                                    command.maxWaitMin(),
                                    command.sameGenderOnly(),
                                    command.maxDetourRatio(),
                                    soloRoute.totalDistance(),
                                    soloRoute.totalFare(),
                                    soloRoute.estimated(),
                                    now);
                    RideRequest persisted;
                    try {
                        persisted = rideRequestRepository.saveAndFlush(request);
                    } catch (DataIntegrityViolationException e) {
                        // active_user_id 유니크 위반 = 이미 진행 중인 요청이 있다
                        throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
                    }

                    // 커밋된 뒤에 realtime 이 받아 대기 화면을 갱신한다 (PRD §14.2)
                    eventPublisher.publishEvent(
                            new RideRequestCreated(
                                    persisted.getId(),
                                    persisted.getUserId(),
                                    persisted.getHubId()));
                    return persisted;
                });
    }

    /**
     * 내 진행 중인 요청 (FR-09).
     *
     * <p>1인 1건이므로 최대 한 건이다. <b>없으면 빈 값이다 — 404 가 아니다.</b>
     * 대기 화면이 처음 들어올 때와 WebSocket 이 끊겼을 때의 폴백으로 쓴다.
     */
    @Transactional(readOnly = true)
    public Optional<RideRequestResponse> findMyRequest(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return rideRequestRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, IN_PROGRESS)
                .map(request -> toResponse(request, now));
    }

    /**
     * 요청을 취소한다 (FR-08).
     *
     * <p>본인의 <b>대기 중</b> 요청만 취소할 수 있다. 이미 매칭·확정됐거나 끝난 요청이면
     * 엔티티가 거절한다. 확정된 그룹에서 빠지는 것은 취소가 아니라
     * {@code POST /api/groups/{groupId}/reject} 다.
     */
    @Transactional
    public void cancel(Long userId, Long requestId) {
        RideRequest request =
                rideRequestRepository
                        .findById(requestId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, "요청을 찾을 수 없습니다."));

        if (!request.isOwnedBy(userId)) {
            // api-spec 이 남의 요청에 403 을 명시한다. 404 와 구분되므로 "그 id 의 요청이 있다"는
            // 사실 자체는 드러난다. 요청 id 는 순번이라 추측이 쉬우니, 민감해지면 404 로 합치자고
            // [계약 변경 요청]을 내는 편이 낫다
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 요청만 취소할 수 있습니다.");
        }

        request.cancel();

        // 커밋된 뒤에 realtime 이 받아 대기 화면을 닫는다
        eventPublisher.publishEvent(new RideRequestCancelled(request.getId(), userId));
        log.info("매칭 요청 취소 requestId={} userId={}", requestId, userId);
    }

    /** 엔티티 → 응답. 거점 정보와 후보 수를 함께 채운다 */
    private RideRequestResponse toResponse(RideRequest request, LocalDateTime now) {
        HubInfo hub = findHub(request.getHubId());
        return RideRequestResponse.of(request, hub, candidateCount(request), now);
    }

    // ── 검증 ────────────────────────────────────────────────

    /** 화면에 없는 값이 직접 호출로 들어오는 것을 막는다. 허용 목록은 설정에 있다 (FR-07) */
    private void validateMaxWaitMinutes(int maxWaitMin) {
        if (!rideProperties.allowedMaxWaitMinutes().contains(maxWaitMin)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "최대 대기 시간은 "
                            + rideProperties.allowedMaxWaitMinutes()
                            + "분 중에서 선택할 수 있습니다.");
        }
    }

    /** 화면에 없는 우회율이 직접 호출로 들어오는 것을 막는다 (FR-07) */
    private void validateDetourRatio(BigDecimal ratio) {
        boolean allowed =
                rideProperties.allowedDetourRatios().stream()
                        // BigDecimal 의 equals 는 소수 자릿수까지 본다. 0.2 와 0.20 을 같게 보려면 compareTo
                        .anyMatch(candidate -> candidate.compareTo(ratio) == 0);
        if (!allowed) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "최대 우회율은 " + rideProperties.allowedDetourRatios() + " 중에서 선택할 수 있습니다.");
        }
    }

    private void requireActiveUser(Long userId) {
        UserSummary user =
                userPort
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.status() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
    }

    /** 1인 1건. 진행 중인 요청이 있으면 새로 만들 수 없다 (FR-08) */
    private void requireNoRequestInProgress(Long userId) {
        if (rideRequestRepository.existsByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
        }
    }

    private HubInfo findHub(Long hubId) {
        return hubPort
                .findById(hubId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.NOT_FOUND, "출발 거점을 찾을 수 없습니다."));
    }

    /**
     * 거점과 목적지가 너무 가까우면 거절한다 (E-08).
     *
     * <p>택시를 탈 이유가 없는 거리이기도 하고, 우회율 계산의 분모가 0에 가까워져 숫자가
     * 망가지기 때문이다. 판정은 직선거리로 한다 — 카카오를 부르기 전에 걸러야 호출을 아낀다.
     */
    private void requireFarEnough(HubInfo hub, double destLat, double destLng) {
        double meters = GeoUtils.haversineMeters(hub.lat(), hub.lng(), destLat, destLng);
        if (meters < rideProperties.minDistanceMeters()) {
            throw new BusinessException(
                    ErrorCode.REQUEST_TOO_SHORT,
                    "거점에서 "
                            + Math.round(meters)
                            + "m 떨어진 목적지입니다. "
                            + rideProperties.minDistanceMeters()
                            + "m 이상인 곳을 선택해 주세요.");
        }
    }

    /** 혼자 갈 때의 거리·요금. 경유지 없이 거점 → 목적지 직행 */
    private RouteResult soloRoute(HubInfo hub, double destLat, double destLng) {
        return routeProvider.findRoute(
                new Coordinate(hub.lat(), hub.lng()), List.of(), new Coordinate(destLat, destLng));
    }

    /**
     * 같은 거점에서 함께 기다리는 사람 수 (나 자신 제외).
     *
     * <p>집계는 WAITING 만 세므로, 내 요청이 이미 MATCHED·CONFIRMED 면 나는 애초에 포함돼
     * 있지 않다. 그때까지 1을 빼면 남의 대기자를 한 명 덜 세게 된다.
     */
    private int candidateCount(RideRequest request) {
        long sameHubWaiting =
                rideRequestRepository.countByHubIdAndStatus(
                        request.getHubId(), RideRequestStatus.WAITING);
        long others =
                request.getStatus() == RideRequestStatus.WAITING
                        ? sameHubWaiting - 1
                        : sameHubWaiting;
        return (int) Math.max(0L, others);
    }
}
