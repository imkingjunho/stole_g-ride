package com.gachiga.realtime.chat;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 그룹 확정·완료에 맞춰 채팅방을 열고 닫는다 (T1-12·T1-13, FR-20·FR-21).
 *
 * <p>따로 "방"을 만들지는 않는다 — {@code /topic/chat/{groupId}} 구독과 {@code ChatMessage.groupId}
 * 만으로 충분하다. 확정되면 첫 안내 메시지를 남기고, 완료되면 메시지를 즉시 지운다(3시간 경과에
 * 의한 정리는 {@link ChatMessageCleanupScheduler} 가 맡는다).
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
    private final ChatMessageRepository chatMessageRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupConfirmed event) {
        try {
            chatService.systemMessage(event.groupId(), CONFIRMED_MESSAGE);
        } catch (RuntimeException e) {
            log.error("채팅방 개설 시스템 메시지 실패 groupId={}", event.groupId(), e);
        }
    }

    /** 탑승이 끝났다 — 더 볼 일 없는 채팅을 바로 지운다 (T1-13, FR-21) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupCompleted event) {
        try {
            long deleted = chatMessageRepository.deleteByGroupId(event.groupId());
            log.info("채팅 메시지 물리 삭제 groupId={} 탑승 완료 {}건", event.groupId(), deleted);
        } catch (RuntimeException e) {
            log.error("채팅 메시지 삭제 실패 groupId={}", event.groupId(), e);
        }
    }
}
