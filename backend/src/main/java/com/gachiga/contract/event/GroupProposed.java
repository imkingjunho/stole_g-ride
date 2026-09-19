package com.gachiga.contract.event;

import java.util.List;

/**
 * 그룹이 만들어져 수락을 기다린다 (P1 의 60초 수락/거절).
 *
 * <p>P0 에서는 수락 절차가 없으므로 이 이벤트와 {@link GroupConfirmed} 를 연달아 발행한다.
 *
 * <p>발행 {@code matching/}(서준) → 수신 {@code realtime/}(임승현, 매칭 알림 push).
 *
 * @param groupId 그룹 id
 * @param userIds 구성원 사용자 id 목록. null 아님
 */
public record GroupProposed(Long groupId, List<Long> userIds) {}
