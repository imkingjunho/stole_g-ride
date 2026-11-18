package com.gachiga.realtime.chat;

import com.gachiga.common.exception.*;
import com.gachiga.contract.matching.MatchHistoryPort;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ChatAccess {
    private final MatchHistoryPort groups;
    private final ChatProperties properties;
    private final Clock clock;

    public void requireOpen(Long groupId) {
        var opened = groups.confirmedAt(groupId);
        if (opened.isEmpty()
                || !LocalDateTime.now(clock)
                        .isBefore(opened.get().plusHours(properties.retentionHours())))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "채팅 보관 시간이 지났거나 종료된 그룹입니다.");
    }
}
