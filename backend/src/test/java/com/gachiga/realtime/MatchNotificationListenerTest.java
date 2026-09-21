package com.gachiga.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupDissolved;
import com.gachiga.contract.event.GroupProposed;
import com.gachiga.contract.event.GroupRecalculated;
import com.gachiga.realtime.dto.MatchNotification;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * {@link MatchNotificationListener} 검증. {@link SimpMessagingTemplate} 은 Mock 이다 — 실제 브로커·
 * 세션 없이 push 대상·본문만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class MatchNotificationListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    private MatchNotificationListener listener;

    @Nested
    @DisplayName("이벤트별 push")
    class EventPush {

        @Test
        @DisplayName("GroupProposed → 구성원 전원에게 PROPOSED push")
        void onProposed() {
            listener = new MatchNotificationListener(messagingTemplate);
            listener.on(new GroupProposed(17L, List.of(1L, 2L)));

            verify(messagingTemplate)
                    .convertAndSendToUser(eq("1"), eq("/queue/match"), eq(MatchNotification.proposed(17L)));
            verify(messagingTemplate)
                    .convertAndSendToUser(eq("2"), eq("/queue/match"), eq(MatchNotification.proposed(17L)));
        }

        @Test
        @DisplayName("GroupConfirmed → CONFIRMED push")
        void onConfirmed() {
            listener = new MatchNotificationListener(messagingTemplate);
            listener.on(new GroupConfirmed(17L, List.of(1L)));

            verify(messagingTemplate)
                    .convertAndSendToUser(eq("1"), eq("/queue/match"), eq(MatchNotification.confirmed(17L)));
        }

        @Test
        @DisplayName("GroupRecalculated → RECALCULATED push")
        void onRecalculated() {
            listener = new MatchNotificationListener(messagingTemplate);
            listener.on(new GroupRecalculated(17L, List.of(1L)));

            verify(messagingTemplate)
                    .convertAndSendToUser(
                            eq("1"), eq("/queue/match"), eq(MatchNotification.recalculated(17L)));
        }

        @Test
        @DisplayName("GroupDissolved → 사유를 담은 DISSOLVED push")
        void onDissolved() {
            listener = new MatchNotificationListener(messagingTemplate);
            listener.on(new GroupDissolved(17L, List.of(1L), "MEMBER_LEFT"));

            verify(messagingTemplate)
                    .convertAndSendToUser(
                            eq("1"),
                            eq("/queue/match"),
                            eq(MatchNotification.dissolved(17L, "MEMBER_LEFT")));
        }

        @Test
        @DisplayName("GroupCompleted → COMPLETED push")
        void onCompleted() {
            listener = new MatchNotificationListener(messagingTemplate);
            listener.on(new GroupCompleted(17L, List.of(1L)));

            verify(messagingTemplate)
                    .convertAndSendToUser(eq("1"), eq("/queue/match"), eq(MatchNotification.completed(17L)));
        }
    }

    @Test
    @DisplayName("구성원이 없으면 아무것도 보내지 않는다")
    void doesNothingWithoutMembers() {
        listener = new MatchNotificationListener(messagingTemplate);
        listener.on(new GroupConfirmed(17L, List.of()));

        verify(messagingTemplate, times(0))
                .convertAndSendToUser(any(), any(), any(MatchNotification.class));
    }

    @Test
    @DisplayName("한 명에게 push가 실패해도 나머지는 받는다")
    void toleratesPerUserFailure() {
        listener = new MatchNotificationListener(messagingTemplate);
        willThrow(new MessagingException("연결 끊김"))
                .given(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/match"), any(MatchNotification.class));

        listener.on(new GroupConfirmed(17L, List.of(1L, 2L)));

        verify(messagingTemplate)
                .convertAndSendToUser(eq("2"), eq("/queue/match"), eq(MatchNotification.confirmed(17L)));
    }
}
