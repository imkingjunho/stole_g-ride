package com.gachiga.ride;

import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final RideRequestRepository rideRequestRepository;
    private final RideProperties rideProperties;
    private final RideCandidateCounter candidateCounter;
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
     * <p>임승현의 realtime 이 5초마다 불러 대기 화면에 밀어 준다.
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
     * 상태 전이를 적용한다. 없는 요청이면 조용히 넘어간다.
     *
     * <p>호출하는 쪽은 {@code matching} 의 이벤트 처리기라, 여기서 예외를 던지면 그쪽
     * 흐름이 깨진다. 이미 사라진 요청에 대한 전이는 무시해도 안전하다.
     */
    private void apply(Long requestId, java.util.function.Consumer<RideRequest> transition, String label) {
        rideRequestRepository
                .findById(requestId)
                .ifPresentOrElse(
                        transition,
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
