package com.gachiga.realtime.chat;

import com.gachiga.contract.event.GroupConfirmed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 그룹이 확정되면 채팅방을 연다 (T1-12, FR-20).
 *
 * <p>따로 "방"을 만들지는 않는다 — {@code /topic/chat/{groupId}} 구독과 {@code ChatMessage.groupId}
 * 만으로 충분하다. 이 리스너가 하는 일은 첫 안내 메시지를 남기는 것뿐이다.
 *
 * <p>서준의 {@code matching} 이 발행하고 여기서 받는다. <b>수신 측은 절대 예외를 밖으로 내보내지
 * 않는다</b>(CLAUDE.md §3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomEventListener {

    private static final String CONFIRMED_MESSAGE = "매칭이 성사됐어요. 만날 장소를 정해 보세요";

    private final ChatService chatService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupConfirmed event) {
        try {
            chatService.systemMessage(event.groupId(), CONFIRMED_MESSAGE);
        } catch (RuntimeException e) {
            log.error("채팅방 개설 시스템 메시지 실패 groupId={}", event.groupId(), e);
        }
    }
}
