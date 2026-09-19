package com.gachiga.contract.ride;

/**
 * 대기 화면에 보여 줄 상태 (FR-09). WebSocket {@code /user/queue/status} 본문이 이 형태다 (PRD §14.5).
 *
 * @param requestId        대상 요청 id. null 아님
 * @param status           요청 상태 문자열. {@code WAITING·MATCHED·CONFIRMED·COMPLETED·CANCELLED·EXPIRED}
 *                         중 하나. 비교할 때는 {@code equals} 를 쓴다. null 아님
 * @param remainingSeconds 만료까지 남은 시간(초). 이미 지났으면 0
 * @param candidateCount   같은 거점에서 함께 기다리는 사람 수(나 자신 제외). 0 이상
 */
public record QueueStatus(
        Long requestId, String status, int remainingSeconds, int candidateCount) {}
