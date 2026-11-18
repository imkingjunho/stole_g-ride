package com.gachiga.realtime.chat;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;

/** 완료 직후에도 기존 대화를 유지한다. 개설 후 보관 기한은 정리 스케줄러가 적용한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomEventListener {
    private final ChatService chatService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupConfirmed event) {
        announce(event.groupId(), "매칭이 성사됐어요. 만남 안내를 확인하고 정확한 위치를 이야기해 보세요.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupCompleted event) {
        announce(event.groupId(), "모두 탑승 완료했어요. 함께한 이동에 저장됐고, 채팅은 기존 보관 기한까지 계속할 수 있어요.");
    }

    private void announce(Long groupId, String message) {
        try {
            chatService.systemMessage(groupId, message);
        } catch (RuntimeException e) {
            log.error("채팅 안내 실패 groupId={}", groupId, e);
        }
    }
}
