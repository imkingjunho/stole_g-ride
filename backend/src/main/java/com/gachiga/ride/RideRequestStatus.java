package com.gachiga.ride;

/**
 * 매칭 요청의 상태 (PRD §7.1 {@code ride_requests.status}).
 *
 * <p>정상 흐름은 {@code WAITING → MATCHED → CONFIRMED → COMPLETED} 이고,
 * 중간에 {@code CANCELLED}(사용자 취소)나 {@code EXPIRED}(대기 시간 만료)로 빠질 수 있다.
 * 그룹이 해체되면 {@code MATCHED·CONFIRMED} 에서 {@code WAITING} 으로 되돌아온다 (E-01).
 *
 * <p>이 enum 은 {@code ride} 모듈 내부용이다. 다른 모듈에는 {@code contract.ride.QueueStatus}
 * 의 문자열로 전달된다 — 이름은 여기 상수와 같다.
 */
public enum RideRequestStatus {
    /** 대기열에 있음. 매칭 대상 */
    WAITING,
    /** 그룹에 배정됨. 수락 대기 (P0 에서는 곧바로 CONFIRMED 로 이어진다) */
    MATCHED,
    /** 그룹 확정. 탑승 전 */
    CONFIRMED,
    /** 사용자가 취소함 */
    CANCELLED,
    /** 대기 시간이 지나 자동 종료됨 (FR-10) */
    EXPIRED,
    /** 탑승 완료 */
    COMPLETED;

    /**
     * 아직 끝나지 않은 요청인지. 1인 1건 제한(FR-08)과 내 요청 조회에 쓴다.
     *
     * @return WAITING·MATCHED·CONFIRMED 면 true
     */
    public boolean isInProgress() {
        return this == WAITING || this == MATCHED || this == CONFIRMED;
    }

    /** 더 이상 상태가 바뀌지 않는 종료 상태인지 */
    public boolean isFinished() {
        return !isInProgress();
    }
}
