package com.gachiga.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.matching.MatchHistoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/** {@link ChatSubscriptionInterceptor} 검증. {@link MatchHistoryPort} 는 Mock 이다. */
@ExtendWith(MockitoExtension.class)
class ChatSubscriptionInterceptorTest {

    @Mock private MatchHistoryPort matchHistoryPort;

    private ChatSubscriptionInterceptor interceptor;

    @Nested
    @DisplayName("채팅 구독")
    class ChatSubscription {

        @Test
        @DisplayName("구성원이면 통과시킨다")
        void allowsMember() {
            interceptor = new ChatSubscriptionInterceptor(matchHistoryPort);
            given(matchHistoryPort.isMember(17L, 3L)).willReturn(true);
            Message<byte[]> message = subscribe("/topic/chat/17", "3");

            assertThat(interceptor.preSend(message, null)).isSameAs(message);
        }

        @Test
        @DisplayName("구성원이 아니면 GROUP_NOT_MEMBER")
        void rejectsNonMember() {
            interceptor = new ChatSubscriptionInterceptor(matchHistoryPort);
            given(matchHistoryPort.isMember(17L, 3L)).willReturn(false);
            Message<byte[]> message = subscribe("/topic/chat/17", "3");

            assertThatThrownBy(() -> interceptor.preSend(message, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_NOT_MEMBER);
        }

        @Test
        @DisplayName("Principal 이 없으면 UNAUTHORIZED")
        void rejectsWithoutPrincipal() {
            interceptor = new ChatSubscriptionInterceptor(matchHistoryPort);
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/chat/17");
            Message<byte[]> message =
                    MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("채팅 구독이 아니면 그냥 통과시킨다")
    void ignoresNonChatDestinations() {
        interceptor = new ChatSubscriptionInterceptor(matchHistoryPort);
        Message<byte[]> message = subscribe("/user/queue/status", "3");

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    @DisplayName("SUBSCRIBE 가 아니면 손대지 않는다")
    void ignoresOtherCommands() {
        interceptor = new ChatSubscriptionInterceptor(matchHistoryPort);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    private Message<byte[]> subscribe(String destination, String userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new StompPrincipal(userId));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
