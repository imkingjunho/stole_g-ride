package com.gachiga.realtime;

import com.gachiga.contract.ride.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code /user/queue/status} push 를 한 곳에 모은다 (T2-1, PRD §14.5).
 *
 * <p>주기 push({@link QueueStatusPushScheduler})와 즉시 push({@link QueueStatusEventListener})가
 * 같은 방식으로 보내도록 공유한다.
 */
@Component
@RequiredArgsConstructor
class QueueStatusPusher {

    private static final String DESTINATION = "/queue/status";

    private final SimpMessagingTemplate messagingTemplate;

    void push(Long userId, QueueStatus status) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), DESTINATION, status);
    }
}
