package com.gachiga.realtime;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.auth.AccessTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 인증 (T1-8, PRD §14.5). {@code DevStompUserInterceptor}(Dev 스텁)를 대체한다.
 *
 * <p>CONNECT 헤더 {@code Authorization: Bearer {accessToken}}을 {@link AccessTokenVerifier}
 * ({@code contract.auth}, 구현은 {@code auth/JwtTokenProvider})로 검증해 Principal에 userId를 붙인다.
 * 헤더가 없거나 검증에 실패하면 연결을 거부한다({@code INVALID_TOKEN}) — 임의 사용자로 대체하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtStompUserInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        Long userId =
                accessTokenVerifier
                        .verify(bearerTokenOf(accessor))
                        .orElseThrow(
                                () -> {
                                    log.warn("STOMP CONNECT 인증 실패");
                                    return new BusinessException(ErrorCode.INVALID_TOKEN);
                                });
        accessor.setUser(new StompPrincipal(String.valueOf(userId)));
        return message;
    }

    private String bearerTokenOf(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return "";
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
