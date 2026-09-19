package com.gachiga.contract.event;

import java.util.List;

/**
 * 구성원이 빠져 경로·요금을 다시 계산했다. 남은 사람들에게 <b>재동의</b>를 받아야 한다 (E-01).
 *
 * <p>발행 {@code matching/}(서준) → 수신 {@code realtime/}(임승현, 변경 알림 push).
 *
 * @param groupId 그룹 id
 * @param userIds 남아 있는 구성원 사용자 id 목록. null 아님
 */
public record GroupRecalculated(Long groupId, List<Long> userIds) {}
