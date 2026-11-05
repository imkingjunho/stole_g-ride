package com.gachiga.realtime;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import java.security.Principal;

/**
 * STOMP 세션에 붙이는 최소한의 {@link Principal}. {@link #getName()} 이 사용자 id(문자열)다.
 *
 * <p>CONNECT 시점에 {@code StompHeaderAccessor#setUser} 로 한 번 붙이면, 같은 세션의 이후 프레임
 * (SUBSCRIBE·SEND)에서도 {@code accessor.getUser()} 로 그대로 읽힌다 — Spring 이 세션 속성으로
 * 들고 있기 때문이다.
 */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }

    /**
     * {@link Principal} 의 이름을 사용자 id 로 해석한다. {@code realtime} 하위 여러 곳(구독 인가·채팅
     * 발신)이 같은 방식으로 파싱하므로 한 곳에 모아 둔다.
     *
     * @throws BusinessException {@code principal} 이 없거나 이름이 숫자가 아니면 {@code UNAUTHORIZED}
     */
    public static Long userIdOf(Principal principal) {
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
