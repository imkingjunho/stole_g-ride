package com.gachiga.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.auth.AccessTokenVerifier;
import java.util.Optional;
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

/** {@link JwtStompUserInterceptor} 검증. {@link AccessTokenVerifier} 는 Mock 이다. */
@ExtendWith(MockitoExtension.class)
class JwtStompUserInterceptorTest {

    @Mock private AccessTokenVerifier accessTokenVerifier;

    private JwtStompUserInterceptor interceptor;

    @Nested
    @DisplayName("CONNECT")
    class Connect {

        @Test
        @DisplayName("유효한 Bearer 토큰이면 Principal에 userId를 붙인다")
        void setsUserFromValidToken() {
            interceptor = new JwtStompUserInterceptor(accessTokenVerifier);
            given(accessTokenVerifier.verify("valid-token")).willReturn(Optional.of(42L));
            StompHeaderAccessor accessor = connectAccessor();
            accessor.addNativeHeader("Authorization", "Bearer valid-token");

            interceptor.preSend(message(accessor), null);

            assertThat(accessor.getUser().getName()).isEqualTo("42");
        }

        @Test
        @DisplayName("헤더가 없으면 INVALID_TOKEN으로 연결을 거부한다")
        void rejectsWithoutHeader() {
            interceptor = new JwtStompUserInterceptor(accessTokenVerifier);
            given(accessTokenVerifier.verify("")).willReturn(Optional.empty());
            StompHeaderAccessor accessor = connectAccessor();

            assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("위조·만료된 토큰이면 INVALID_TOKEN으로 연결을 거부한다")
        void rejectsInvalidToken() {
            interceptor = new JwtStompUserInterceptor(accessTokenVerifier);
            given(accessTokenVerifier.verify("bad-token")).willReturn(Optional.empty());
            StompHeaderAccessor accessor = connectAccessor();
            accessor.addNativeHeader("Authorization", "Bearer bad-token");

            assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("Bearer 접두사가 없으면 토큰으로 취급하지 않는다")
        void rejectsHeaderWithoutBearerPrefix() {
            interceptor = new JwtStompUserInterceptor(accessTokenVerifier);
            given(accessTokenVerifier.verify("")).willReturn(Optional.empty());
            StompHeaderAccessor accessor = connectAccessor();
            accessor.addNativeHeader("Authorization", "valid-token");

            assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        private StompHeaderAccessor connectAccessor() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setLeaveMutable(true);
            return accessor;
        }

        private Message<byte[]> message(StompHeaderAccessor accessor) {
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }
    }

    @Test
    @DisplayName("CONNECT가 아니면 손대지 않는다")
    void ignoresOtherCommands() {
        interceptor = new JwtStompUserInterceptor(accessTokenVerifier);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }
}
