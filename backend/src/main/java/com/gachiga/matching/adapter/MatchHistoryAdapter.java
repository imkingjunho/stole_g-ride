package com.gachiga.matching.adapter;

import com.gachiga.contract.matching.MatchHistoryPort;
import com.gachiga.matching.domain.MatchMember;
import com.gachiga.matching.port.MatchMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchHistoryAdapter implements MatchHistoryPort {

    private final MatchMemberRepository memberRepository;

    @Override
    public boolean isMember(Long groupId, Long userId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId).isPresent();
    }

    @Override
    public List<Long> memberUserIds(Long groupId) {
        // 그룹이 없으면 빈 리스트 (MatchHistoryPort 계약)
        return memberRepository.findByGroupId(groupId).stream()
            .map(MatchMember::getUserId)
            .toList();
    }
}
