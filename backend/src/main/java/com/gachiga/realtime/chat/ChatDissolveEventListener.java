package com.gachiga.realtime.chat;

import com.gachiga.contract.event.GroupDissolved;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 그룹 해체 처리 (T2-4, E-10).
 *
 * <p>그룹이 해체되면 안내 메시지를 남기고, <b>5분 뒤</b> 그 방의 메시지를 물리 삭제한다 — 더 볼
 * 일이 없어진 채팅을 계속 들고 있을 이유가 없어서다. {@link ChatMessageCleanupScheduler}(T1-13)의
 * 3시간 정리와 달리 이건 <b>한 번만 도는 예약</b>이다. 서버가 그 사이 재시작되면 예약은 사라지지만,
 * 결국 3시간 정리가 잡아내므로 메시지가 영영 안 지워지지는 않는다.
 *
 * <p>서준의 {@code matching} 이 발행하고 여기서 받는다. <b>수신 측은 절대 예외를 밖으로 내보내지
 * 않는다</b>(CLAUDE.md §3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDissolveEventListener {

    private static final Duration DELETE_DELAY = Duration.ofMinutes(5);

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;
    private final TaskScheduler taskScheduler;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupDissolved event) {
        try {
            chatService.systemMessage(event.groupId(), dissolvedMessage(event.reason()));
            scheduleDeletion(event.groupId());
        } catch (RuntimeException e) {
            log.error("그룹 해체 처리 실패 groupId={}", event.groupId(), e);
        }
    }

    private void scheduleDeletion(Long groupId) {
        taskScheduler.schedule(() -> deleteMessages(groupId), clock.instant().plus(DELETE_DELAY));
        log.info("채팅 메시지 삭제 예약 groupId={} {}분 뒤", groupId, DELETE_DELAY.toMinutes());
    }

    private void deleteMessages(Long groupId) {
        try {
            long deleted = chatMessageRepository.deleteByGroupId(groupId);
            log.info("채팅 메시지 물리 삭제 groupId={} 그룹 해체 5분 경과 {}건", groupId, deleted);
        } catch (RuntimeException e) {
            log.error("채팅 메시지 삭제 실패 groupId={}", groupId, e);
        }
    }

    private String dissolvedMessage(String reason) {
        if ("MEMBER_LEFT".equals(reason)) {
            return "동승자가 나가 그룹이 해체됐어요. 채팅방은 곧 닫힙니다.";
        }
        if ("TOO_FEW_MEMBERS".equals(reason)) {
            return "인원이 부족해 그룹이 해체됐어요. 채팅방은 곧 닫힙니다.";
        }
        return "그룹이 해체됐어요. 채팅방은 곧 닫힙니다.";
    }
}
