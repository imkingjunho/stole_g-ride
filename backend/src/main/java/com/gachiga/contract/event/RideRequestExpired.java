package com.gachiga.contract.event;

/**
 * 대기 시간이 지나 요청이 만료됐다 (FR-10). 발행 {@code ride/}(이승민) → 수신 {@code realtime/}(임승현).
 *
 * @param requestId 만료된 요청 id
 * @param userId    요청한 사용자 id
 */
public record RideRequestExpired(Long requestId, Long userId) {}
