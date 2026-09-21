package com.gachiga.realtime;

import java.security.Principal;

/**
 * STOMP 세션에 붙이는 최소한의 {@link Principal}. {@link #getName()} 이 사용자 id(문자열)다.
 *
 * <p>CONNECT 시점에 {@code StompHeaderAccessor#setUser} 로 한 번 붙이면, 같은 세션의 이후 프레임
 * (SUBSCRIBE·SEND)에서도 {@code accessor.getUser()} 로 그대로 읽힌다 — Spring 이 세션 속성으로
 * 들고 있기 때문이다.
 */
record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
