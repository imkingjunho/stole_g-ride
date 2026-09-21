package com.gachiga.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link DevStompUserInterceptor} 검증. T1-4(로그인)가 풀려 실제 JWT 로 교체되기 전까지의 임시
 * 동작을 확인한다.
 */
class DevStompUserInterceptorTest {

    private final DevStompUserInterceptor interceptor = new DevStompUserInterceptor();

    @Nested
    @DisplayName("CONNECT")
    class Connect {

        @Test
        @DisplayName("X-Dev-User 헤더의 사용자를 Principal 로 붙인다")
        void setsUserFromHeader() {
            StompHeaderAccessor accessor = connectAccessor();
            accessor.addNativeHeader("X-Dev-User", "3");
            Message<byte[]> message = message(accessor);

            interceptor.preSend(message, null);

            assertThat(accessor.getUser().getName()).isEqualTo("3");
        }

        @Test
        @DisplayName("헤더가 없으면 1번으로 처리한다")
        void defaultsToUserOne() {
            StompHeaderAccessor accessor = connectAccessor();
            Message<byte[]> message = message(accessor);

            interceptor.preSend(message, null);

            assertThat(accessor.getUser().getName()).isEqualTo("1");
        }

        @Test
        @DisplayName("숫자가 아니면 1번으로 처리한다")
        void defaultsWhenNotNumeric() {
            StompHeaderAccessor accessor = connectAccessor();
            accessor.addNativeHeader("X-Dev-User", "abc");
            Message<byte[]> message = message(accessor);

            interceptor.preSend(message, null);

            assertThat(accessor.getUser().getName()).isEqualTo("1");
        }

        private StompHeaderAccessor connectAccessor() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            // 헤더 변경이 실제 메시지에 반영되게 두 프레임 사이 캐시된 accessor 를 유지한다
            accessor.setLeaveMutable(true);
            return accessor;
        }

        private Message<byte[]> message(StompHeaderAccessor accessor) {
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }
    }

    @Test
    @DisplayName("CONNECT 가 아니면 손대지 않는다")
    void ignoresOtherCommands() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }
}
