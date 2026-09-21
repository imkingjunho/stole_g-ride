package com.gachiga.realtime;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.matching.MatchHistoryPort;
import java.security.Principal;
import java.util.StringTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * {@code /topic/chat/{groupId}} 구독 인가 (T1-9, FR-20 전제).
 *
 * <p>그룹 구성원만 그 방을 구독할 수 있다. {@link MatchHistoryPort#isMember} 로 확인하고, 아니면
 * {@code GROUP_NOT_MEMBER} 로 거부한다 — 남의 채팅을 엿볼 수 없게 하는 마지막 방어선이다.
 *
 * <p>채팅이 아닌 구독({@code /user/queue/status} 등)은 그냥 통과시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

    private final MatchHistoryPort matchHistoryPort;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        Long groupId = chatGroupIdOf(accessor.getDestination());
        if (groupId == null) {
            return message;
        }

        Long userId = userIdOf(accessor.getUser());
        if (!matchHistoryPort.isMember(groupId, userId)) {
            log.warn("채팅 구독 거부 groupId={} userId={}", groupId, userId);
            throw new BusinessException(ErrorCode.GROUP_NOT_MEMBER);
        }
        return message;
    }

    /**
     * {@code /topic/chat/{groupId}} 형식이면 {@code groupId}, 아니면 {@code null}.
     *
     * <p>문자열 파싱은 {@link StringTokenizer} 를 쓴다 (CLAUDE.md §5).
     */
    private Long chatGroupIdOf(String destination) {
        if (destination == null) {
            return null;
        }
        StringTokenizer tokenizer = new StringTokenizer(destination, "/");
        if (tokenizer.countTokens() != 3) {
            return null;
        }
        if (!"topic".equals(tokenizer.nextToken()) || !"chat".equals(tokenizer.nextToken())) {
            return null;
        }
        try {
            return Long.valueOf(tokenizer.nextToken());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long userIdOf(Principal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "STOMP 세션에 사용자 정보가 없습니다.");
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED, "STOMP 세션 사용자 id 형식이 올바르지 않습니다.");
        }
    }
}
