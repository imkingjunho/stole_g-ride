package com.gachiga.ride;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 {@code ride} 를 들여다보는 유일한 창구 (PRD §14.1).
 *
 * <p>서준의 매칭 엔진은 {@link RideRequestPort} 로 대기 요청을 읽고 상태를 바꾸며,
 * 임승현의 대기 화면 push 는 {@link QueueStatusPort} 로 상태를 묻는다. 두 쪽 모두
 * {@code ride} 의 엔티티를 직접 보지 않으므로, 여기 구조가 바뀌어도 남이 깨지지 않는다.
 *
 * <p>Phase 0 의 {@code InMemoryRideRequestAdapter} 를 대체한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestAdapter implements RideRequestPort, QueueStatusPort {

    /** 진행 중으로 보는 상태. 대기 상태 조회에 쓴다 */
    private static final List<RideRequestStatus> IN_PROGRESS =
            List.of(RideRequestStatus.WAITING, RideRequestStatus.MATCHED, RideRequestStatus.CONFIRMED);

    /**
     * 일괄 조회 때 IN 목록 한 번에 넣을 최대 개수. 너무 길면 DB 가 계획을 잘못 세우거나
     * 패킷 크기 제한에 걸린다. 동시 접속 목표(200명, PRD §10)는 한 번에 들어간다.
     */
    private static final int IN_CHUNK = 500;

    private final RideRequestRepository rideRequestRepository;
    private final RideProperties rideProperties;
    private final RideCandidateCounter candidateCounter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 지금 매칭 대상이 되는 요청 전체.
     *
     * <p>만료가 {@code matchCutoffSeconds}(기본 30초) 이내로 남은 것은 뺀다. 곧 사라질 요청을
     * 새 그룹에 넣으면 성사 직후 만료돼 그룹이 깨지기 때문이다 (E-04).
     */
    @Override
    @Transactional(readOnly = true)
    public List<WaitingRequest> findWaiting() {
        LocalDateTime cutoff =
                LocalDateTime.now(clock).plusSeconds(rideProperties.matchCutoffSeconds());
        return rideRequestRepository
                .findByStatusAndExpiresAtAfterOrderByDepartAtAsc(RideRequestStatus.WAITING, cutoff)
                .stream()
                .map(RideRequestAdapter::toWaitingRequest)
                .toList();
    }

    /**
     * WAITING → MATCHED. 이미 남이 가져갔으면 false (E-02).
     *
     * <p>조건부 UPDATE 한 문장으로 처리하므로 검사와 변경 사이에 끼어들 틈이 없다.
     */
    @Override
    @Transactional
    public boolean tryMarkMatched(Long requestId, int version) {
        int updated = rideRequestRepository.markMatchedIfVersionMatches(requestId, version);
        if (updated == 0) {
            log.debug("배정 실패 — 이미 다른 그룹이 가져갔다 requestId={} version={}", requestId, version);
            return false;
        }
        // 매칭된 요청은 대기열에서 빠져야 한다. 커밋 뒤에 뺀다
        eventPublisher.publishEvent(new RideRequestStatusChanged(requestId));
        return true;
    }

    /** 그룹이 해체돼 대기열로 돌아온다 (E-01). 남은 시간은 그대로 둔다 */
    @Override
    @Transactional
    public void markWaiting(Long requestId) {
        apply(requestId, RideRequest::markWaiting, "재대기");
    }

    @Override
    @Transactional
    public void markConfirmed(Long requestId) {
        apply(requestId, RideRequest::markConfirmed, "확정");
    }

    @Override
    @Transactional
    public void markCompleted(Long requestId) {
        apply(requestId, RideRequest::markCompleted, "완료");
    }

    /**
     * 사용자의 진행 중 요청 상태 (FR-09).
     *
     * <p>이벤트를 받아 한 사람에게 바로 push 할 때 쓴다. 연결된 사용자 전원을 주기적으로 돌 때는
     * {@link #statusOfAll} 을 써야 한다 — 사람마다 이 메서드를 부르면 쿼리가 사람 수 × 2 로 나간다.
     * 진행 중인 요청이 없으면 빈 값이다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<QueueStatus> statusOf(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return rideRequestRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, IN_PROGRESS)
                .map(
                        request ->
                                new QueueStatus(
                                        request.getId(),
                                        request.getStatus().name(),
                                        request.remainingSeconds(now),
                                        candidateCounter.countFor(request)));
    }

    /**
     * 여러 사용자의 진행 중 요청 상태를 한 번에 (T2-3).
     *
     * <p>쿼리는 500명까지 두 개다 — 사용자들의 진행 중 요청 한 번, 그 요청들이 속한 거점의 대기 인원
     * 한 번. 500명을 넘으면 사용자 조회가 500명 단위로 나뉜다. 사람마다 {@link #statusOf} 를 부르면
     * 사람 수의 두 배가 나간다.
     *
     * <p>값은 {@link #statusOf} 와 같다. 사용자별로 가장 최근 요청을 고르고, 후보 수는 같은
     * {@link RideCandidateCounter#othersWaiting} 규칙으로 센다.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, QueueStatus> statusOfAll(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(userIds));

        Map<Long, RideRequest> latestByUser = new HashMap<>();
        for (List<Long> chunk : chunks(distinct)) {
            for (RideRequest request : rideRequestRepository.findByUserIdInAndStatusIn(chunk, IN_PROGRESS)) {
                latestByUser.merge(
                        request.getUserId(),
                        request,
                        (a, b) -> LATEST_FIRST.compare(a, b) <= 0 ? a : b);
            }
        }
        if (latestByUser.isEmpty()) {
            return Map.of();
        }

        List<Long> hubIds =
                latestByUser.values().stream().map(RideRequest::getHubId).distinct().toList();
        Map<Long, Long> waitingByHub = new HashMap<>();
        for (List<Long> chunk : chunks(hubIds)) {
            for (HubWaitingCount row : rideRequestRepository.countWaitingByHubIds(chunk)) {
                waitingByHub.put(row.hubId(), row.count());
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        return latestByUser.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> {
                                    RideRequest request = e.getValue();
                                    long sameHubWaiting =
                                            waitingByHub.getOrDefault(request.getHubId(), 0L);
                                    return new QueueStatus(
                                            request.getId(),
                                            request.getStatus().name(),
                                            request.remainingSeconds(now),
                                            candidateCounter.othersWaiting(request, sameHubWaiting));
                                }));
    }

    /**
     * 가장 최근에 만든 요청이 앞에 온다 — 단건 조회({@code OrderByCreatedAtDesc})와 같은 기준이다.
     *
     * <p>진행 중 요청은 사용자당 최대 한 건(active_user_id UNIQUE)이라 동률은 실제로 생기지 않는다.
     * 그래도 결과가 흔들리지 않게 id 로 한 번 더 가른다.
     */
    private static final Comparator<RideRequest> LATEST_FIRST =
            Comparator.comparing(RideRequest::getCreatedAt)
                    .thenComparing(RideRequest::getId)
                    .reversed();

    private static <T> List<List<T>> chunks(List<T> items) {
        return chunks(items, IN_CHUNK);
    }

    /** {@code size} 개씩 끊는다. 마지막 조각은 짧을 수 있다. 테스트가 작은 크기로 경계를 확인한다 */
    static <T> List<List<T>> chunks(List<T> items, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < items.size(); from += size) {
            chunks.add(items.subList(from, Math.min(items.size(), from + size)));
        }
        return chunks;
    }

    /**
     * 상태 전이를 적용한다. 없는 요청이면 조용히 넘어간다.
     *
     * <p>호출하는 쪽은 {@code matching} 의 이벤트 처리기라, 여기서 예외를 던지면 그쪽
     * 흐름이 깨진다. 이미 사라진 요청에 대한 전이는 무시해도 안전하다.
     */
    private void apply(Long requestId, java.util.function.Consumer<RideRequest> transition, String label) {
        rideRequestRepository
                .findById(requestId)
                .ifPresentOrElse(
                        request -> {
                            try {
                                transition.accept(request);
                            } catch (BusinessException e) {
                                // 그룹에 없는 요청에 확정·해체·완료를 걸었다. 매칭 흐름을 깨지 않도록 삼킨다
                                log.warn(
                                        "{} 처리를 건너뛴다 requestId={} status={} — {}",
                                        label,
                                        requestId,
                                        request.getStatus(),
                                        e.getMessage());
                                return;
                            }
                            // 재대기면 대기열에 다시 넣고, 확정·완료면 뺀다. 커밋 뒤에 맞춘다
                            eventPublisher.publishEvent(new RideRequestStatusChanged(requestId));
                        },
                        () -> log.warn("{} 처리 대상을 찾지 못했다 requestId={}", label, requestId));
    }

    /** 엔티티를 계약 레코드로 옮긴다. 엔티티를 그대로 내보내지 않는다 (PRD §9.3) */
    private static WaitingRequest toWaitingRequest(RideRequest request) {
        return new WaitingRequest(
                request.getId(),
                request.getUserId(),
                request.getHubId(),
                new Coordinate(request.getDestLat(), request.getDestLng()),
                request.getDestName(),
                request.getDepartAt(),
                request.getExpiresAt(),
                request.getCreatedAt(),
                request.isSameGenderOnly(),
                request.getMaxDetourRatio(),
                request.getSoloDistance(),
                request.getSoloFare(),
                request.getVersion());
    }
}
