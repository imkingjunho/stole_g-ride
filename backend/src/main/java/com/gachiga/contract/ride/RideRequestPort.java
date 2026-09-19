package com.gachiga.contract.ride;

import java.util.List;

/**
 * 대기 요청 읽기와 상태 변경. 구현은 {@code ride/} (이승민), 사용은 서준 {@code matching/} 이다.
 *
 * <p>Phase 0 에는 {@code ride/InMemoryRideRequestAdapter} 가 메모리로 흉내 낸다.
 */
public interface RideRequestPort {

    /**
     * 지금 매칭 대상이 되는 요청 전체.
     *
     * <p>{@code status=WAITING} 이면서 <b>만료까지 30초 넘게 남은</b> 것만 담는다. 곧 만료될 요청을
     * 새 그룹에 넣으면 성사 직후 만료되기 때문이다 (E-04).
     *
     * @return 대기 요청 목록. 비어 있을 수 있으나 null 아님
     */
    List<WaitingRequest> findWaiting();

    /**
     * 요청을 WAITING → MATCHED 로 바꾼다.
     *
     * <p>같은 요청을 두 그룹이 동시에 집어 가는 것을 막는 지점이다. 그 사이 다른 그룹이 먼저
     * 가져갔다면 버전이 어긋나 {@code false} 가 돌아오고, 그 배정은 폐기해야 한다 (E-02).
     *
     * @param requestId 대상 요청 id
     * @param version   {@link WaitingRequest#version()} 에서 받은 값 그대로
     * @return 이번 호출이 배정에 성공했으면 true. 이미 남이 가져갔으면 false
     */
    boolean tryMarkMatched(Long requestId, int version);

    /**
     * 요청을 다시 WAITING 으로 되돌린다. 그룹이 해체됐을 때 쓴다 (E-01).
     * 남은 대기 시간({@code expiresAt})은 그대로 유지된다.
     *
     * @param requestId 대상 요청 id. null 아님
     */
    void markWaiting(Long requestId);

    /**
     * 그룹 전원이 수락해 확정됐을 때 CONFIRMED 로 바꾼다.
     *
     * @param requestId 대상 요청 id. null 아님
     */
    void markConfirmed(Long requestId);

    /**
     * 탑승이 끝났을 때 COMPLETED 로 바꾼다.
     *
     * @param requestId 대상 요청 id. null 아님
     */
    void markCompleted(Long requestId);
}
