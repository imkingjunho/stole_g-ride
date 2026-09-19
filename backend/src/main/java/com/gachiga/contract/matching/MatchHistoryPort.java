package com.gachiga.contract.matching;

import java.util.List;

/**
 * 그룹 구성원 확인. 구현은 {@code matching/} (서준), 사용은 임승현 {@code realtime/}(채팅 구독 인가)과
 * {@code user/}(신고 검증)이다.
 *
 * <p>Phase 0 에는 {@code matching/StubMatchHistoryAdapter} 가 항상 true / 빈 리스트를 돌려준다.
 */
public interface MatchHistoryPort {

    /**
     * 사용자가 그 그룹의 구성원인지 확인한다. 채팅방 구독을 허용할지 판단하는 기준이다.
     *
     * @param groupId 대상 그룹 id
     * @param userId  확인할 사용자 id
     * @return 구성원이면 true. 그룹이 없거나 구성원이 아니면 false
     */
    boolean isMember(Long groupId, Long userId);

    /**
     * 그룹 구성원의 사용자 id 목록. 시스템 메시지를 전원에게 보낼 때 쓴다.
     *
     * @param groupId 대상 그룹 id
     * @return 사용자 id 목록. 그룹이 없으면 빈 리스트. null 아님
     */
    List<Long> memberUserIds(Long groupId);
}
