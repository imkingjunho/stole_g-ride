package com.gachiga.realtime;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupDissolved;
import com.gachiga.contract.event.GroupProposed;
import com.gachiga.contract.event.GroupRecalculated;
import com.gachiga.realtime.dto.MatchNotification;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 매칭 관련 사건을 받아 구성원 각자의 {@code /user/queue/match} 로 push 한다 (T1-10, FR-12, PRD §14.5).
 *
 * <p>서준의 {@code matching} 이 발행하고 여기서 받는다. 직접 호출이 아니라 이벤트인 이유는 두 모듈이
 * 서로를 몰라도 되게 하기 위해서다 (PRD §14.2).
 *
 * <p><b>수신 측은 절대 예외를 밖으로 내보내지 않는다</b>(CLAUDE.md §3). 한 사람에게 push 가 실패해도
 * 나머지는 받아야 하므로 사용자별로 감싼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchNotificationListener {

    private static final String DESTINATION = "/queue/match";

    private final SimpMessagingTemplate messagingTemplate;

    /** P0 에서는 수락 절차가 없어 {@link GroupConfirmed} 와 연달아 발행된다 (PRD §14.2) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupProposed event) {
        push(event.userIds(), MatchNotification.proposed(event.groupId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupConfirmed event) {
        push(event.userIds(), MatchNotification.confirmed(event.groupId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupRecalculated event) {
        push(event.userIds(), MatchNotification.recalculated(event.groupId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupDissolved event) {
        push(event.userIds(), MatchNotification.dissolved(event.groupId(), event.reason()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GroupCompleted event) {
        push(event.userIds(), MatchNotification.completed(event.groupId()));
    }

    private void push(List<Long> userIds, MatchNotification notification) {
        if (userIds == null || userIds.isEmpty()) {
            log.warn("{} 알림에 구성원이 없다 groupId={}", notification.type(), notification.groupId());
            return;
        }

        int sent = 0;
        for (Long userId : userIds) {
            try {
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(userId), DESTINATION, notification);
                sent++;
            } catch (RuntimeException e) {
                log.error(
                        "매칭 알림 push 실패 type={} groupId={} userId={}",
                        notification.type(),
                        notification.groupId(),
                        userId,
                        e);
            }
        }
        log.info(
                "매칭 알림 push type={} groupId={} {}/{}명",
                notification.type(),
                notification.groupId(),
                sent,
                userIds.size());
    }
}
