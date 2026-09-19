package com.gachiga.matching;

import com.gachiga.contract.matching.MatchHistoryPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ⚠️ <b>Phase 0 스텁</b> — 그룹이 아직 없으므로 "구성원 맞다"고만 답한다 (PRD §14.7).
 *
 * <p>{@link #isMember} 가 늘 {@code true} 인 덕분에 임승현이 채팅 구독을, 신고 검증을 먼저 붙여 볼 수 있다.
 * <b>인가 검사가 실질적으로 꺼져 있는 상태</b>라는 뜻이므로, 그룹이 생기기 전까지만 유효하다.
 *
 * <p><b>교체 담당: 서준 · Phase 1.</b> {@code match_members} 를 조회하는 구현으로 바꾸고 이 클래스를 삭제한다.
 */
@Component
public class StubMatchHistoryAdapter implements MatchHistoryPort {

    @Override
    public boolean isMember(Long groupId, Long userId) {
        return true;
    }

    @Override
    public List<Long> memberUserIds(Long groupId) {
        return List.of();
    }
}
