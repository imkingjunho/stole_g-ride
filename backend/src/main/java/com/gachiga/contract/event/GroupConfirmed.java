package com.gachiga.contract.event;

import java.util.List;

/**
 * 그룹이 확정됐다(전원 수락). 채팅방 개설 트리거다.
 *
 * <p>발행 {@code matching/}(서준) → 수신 {@code realtime/}(임승현, 채팅방 개설·알림),
 * {@code ride/}(이승민, 요청을 CONFIRMED 로).
 *
 * @param groupId 그룹 id
 * @param userIds 구성원 사용자 id 목록. null 아님
 */
public record GroupConfirmed(Long groupId, List<Long> userIds) {}
