package com.gachiga.ride;

/**
 * 요청 상태가 바뀌었다는 {@code ride} 내부 신호. 다른 모듈에는 나가지 않는다.
 *
 * <p>{@link RideQueueSynchronizer} 가 커밋 뒤에 받아 대기열을 지금 상태에 맞춘다 — WAITING 이면 넣고
 * 아니면 뺀다. 생성·취소·만료는 계약 이벤트로 이미 맞추므로, 이 신호는 그 밖의 전이
 * (매칭 배정, 확정, 완료, 해체 뒤 재대기)에 쓴다.
 *
 * @param requestId 상태가 바뀐 요청 id
 */
record RideRequestStatusChanged(Long requestId) {}
