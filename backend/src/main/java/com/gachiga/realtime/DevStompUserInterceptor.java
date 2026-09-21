package com.gachiga.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * ⚠️ <b>T1-8 자리의 Dev 스텁</b> — CONNECT 프레임의 {@code X-Dev-User} 헤더로 STOMP 세션의 사용자를
 * 정한다.
 *
 * <p>REST 의 {@code auth/DevCurrentUserResolver} 와 같은 패턴이다. PRD §14.5 는 CONNECT 헤더
 * {@code Authorization: Bearer} 를 요구하지만, 로그인(T1-4)이 {@code contract.user.UserPort} 확장
 * 승인 대기로 막혀 있어 <b>실제 토큰을 발급할 수 없다</b>. 그 사이 T1-9~T1-13(구독 인가·알림
 * push·채팅)을 만들고 확인할 수 있도록, 헤더로 사용자를 흉내 낸다.
 *
 * <pre>{@code
 * CONNECT
 * accept-version:1.2
 * host:localhost
 * X-Dev-User:3
 *
 * ^@
 * }</pre>
 *
 * <p>헤더가 없거나 숫자가 아니면 1번으로 처리한다 — 거부하지 않는다. 아직 아무도 로그인할 수 없는
 * 단계에서 연결을 거부하면 실시간 기능 자체를 확인할 방법이 없기 때문이다.
 *
 * <p><b>교체 담당: 임승현 · T1-4(로그인) 계약이 풀리면.</b> {@code Authorization: Bearer} 검증으로
 * 바꾸고 이 클래스를 삭제한다. {@code local}·{@code test} 프로파일에서만 등록된다 — 운영에 실수로
 * 남으면 헤더 하나로 아무나 흉내 낼 수 있기 때문이다.
 */
@Slf4j
@Component
@Profile({"local", "test"})
public class DevStompUserInterceptor implements ChannelInterceptor {

    private static final String DEV_USER_HEADER = "X-Dev-User";
    private static final String DEFAULT_USER_ID = "1";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userId = resolveUserId(accessor);
            accessor.setUser(new StompPrincipal(userId));
            log.info("[DEV] STOMP CONNECT userId={}", userId);
        }
        return message;
    }

    private String resolveUserId(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(DEV_USER_HEADER);
        if (header == null || header.isBlank()) {
            return DEFAULT_USER_ID;
        }
        String trimmed = header.trim();
        try {
            Long.parseLong(trimmed);
            return trimmed;
        } catch (NumberFormatException e) {
            log.warn(
                    "[DEV] {} 헤더가 숫자가 아닙니다: '{}'. {}번 사용자로 처리합니다.",
                    DEV_USER_HEADER,
                    header,
                    DEFAULT_USER_ID);
            return DEFAULT_USER_ID;
        }
    }
}
