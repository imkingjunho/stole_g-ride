package com.gachiga.matching.adapter;

import com.gachiga.contract.matching.MatchHistoryPort;
import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.matching.port.MatchGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchHistoryAdapter implements MatchHistoryPort {

    private final MatchGroupRepository groupRepository;

    @Override
    public boolean isMember(Long groupId, Long userId) {
        return groupRepository.findById(groupId)
            .map(group -> group.getMembers().stream()
                .anyMatch(member -> member.getUserId().equals(userId)))
            .orElse(false);
    }

    @Override
    public List<Long> memberUserIds(Long groupId) {
        return groupRepository.findById(groupId)
            .map(group -> group.getMembers().stream()
                .map(member -> member.getUserId())
                .toList())
            .orElse(null);
    }
}