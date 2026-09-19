package com.gachiga.contract.event;

/**
 * 매칭 요청이 만들어졌다. 발행 {@code ride/}(이승민) → 수신 {@code realtime/}(임승현, 대기 화면 갱신).
 *
 * @param requestId 생성된 요청 id
 * @param userId    요청한 사용자 id
 * @param hubId     출발 거점 id
 */
public record RideRequestCreated(Long requestId, Long userId, Long hubId) {}
