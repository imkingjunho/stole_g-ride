package com.gachiga.contract.event;

/**
 * 사용자가 요청을 취소했다. 발행 {@code ride/}(이승민) → 수신 {@code realtime/}(임승현).
 *
 * @param requestId 취소된 요청 id
 * @param userId    취소한 사용자 id
 */
public record RideRequestCancelled(Long requestId, Long userId) {}
