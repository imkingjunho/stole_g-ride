package com.gachiga.contract.event;

import java.util.List;

/**
 * 탑승이 끝났다. 이력·통계 집계와 채팅방 정리의 기준점이다.
 *
 * <p>발행 {@code matching/}(서준) → 수신 {@code realtime/}(임승현), {@code ride/}(이승민, COMPLETED 로).
 *
 * @param groupId 그룹 id
 * @param userIds 구성원 사용자 id 목록. null 아님
 */
public record GroupCompleted(Long groupId, List<Long> userIds) {}
