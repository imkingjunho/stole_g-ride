package com.gachiga.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.ride.QueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** {@link QueueStatusPusher} 검증. */
@ExtendWith(MockitoExtension.class)
class QueueStatusPusherTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("사용자 id 를 문자열로, /queue/status 로 보낸다")
    void sendsToUserQueueStatus() {
        QueueStatusPusher pusher = new QueueStatusPusher(messagingTemplate);
        QueueStatus status = new QueueStatus(1L, "WAITING", 300, 2);

        pusher.push(3L, status);

        verify(messagingTemplate).convertAndSendToUser(eq("3"), eq("/queue/status"), eq(status));
    }
}
