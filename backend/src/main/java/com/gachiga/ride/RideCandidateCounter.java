package com.gachiga.ride;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "같은 거점에서 함께 기다리는 사람 수" 를 센다 (FR-09).
 *
 * <p>REST 응답({@code GET /api/requests/me})과 WebSocket push({@code QueueStatusPort})가
 * <b>같은 숫자</b>를 보여 줘야 해서 한 곳에 모았다. 두 경로가 각자 계산하면 화면에서
 * 값이 깜빡이며 달라진다.
 */
@Component
@RequiredArgsConstructor
class RideCandidateCounter {

    private final RideRequestRepository rideRequestRepository;

    /**
     * 나를 뺀 같은 거점 대기자 수.
     *
     * <p>집계는 WAITING 만 세므로, 내 요청이 이미 MATCHED·CONFIRMED 면 나는 애초에 포함돼
     * 있지 않다. 그때까지 1을 빼면 남의 대기자를 한 명 덜 세게 된다.
     */
    int countFor(RideRequest request) {
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
