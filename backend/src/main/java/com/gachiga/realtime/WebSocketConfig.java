package com.gachiga.realtime;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 엔드포인트·브로커 설정 (PRD §14.5).
 *
 * <ul>
 *   <li>연결: {@code ws://localhost:8080/ws} (SockJS 미사용)
 *   <li>구독: {@code /user/queue/status}(내 대기 상태), {@code /user/queue/match}(매칭 알림),
 *       {@code /topic/chat/{groupId}}(채팅)
 *   <li>발신: {@code /app/chat/{groupId}}
 * </ul>
 *
 * <p>CONNECT 시점의 사용자 식별은 {@link DevStompUserInterceptor}(T1-8, 아직 Dev 스텁 — 로그인이
 * 계약 승인 대기라 실제 토큰이 없다), 채팅 구독 인가는 {@link ChatSubscriptionInterceptor}(T1-9)가
 * 맡는다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 연결을 허용할 출처. {@code config/CorsConfig} 와 같은 값을 읽는다
     * ({@code application.yml} 의 {@code gachiga.cors.allowed-origins}).
     *
     * <p>두 곳에 따로 적어 두면 배포 때 REST 만 열리고 {@code /ws} 는 막히는 일이 생긴다.
     */
    private final String[] allowedOrigins;

    private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;

    /** {@code local}·{@code test} 에만 있으므로 {@link ObjectProvider} 로 받아 "있으면 등록"한다 */
    private final ObjectProvider<DevStompUserInterceptor> devStompUserInterceptor;

    public WebSocketConfig(
            @Value("${gachiga.cors.allowed-origins}") String[] allowedOrigins,
            ChatSubscriptionInterceptor chatSubscriptionInterceptor,
            ObjectProvider<DevStompUserInterceptor> devStompUserInterceptor) {
        this.allowedOrigins = allowedOrigins;
        this.chatSubscriptionInterceptor = chatSubscriptionInterceptor;
        this.devStompUserInterceptor = devStompUserInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 메모리 브로커. 서버가 한 대인 동안은 이것으로 충분하다
        registry.enableSimpleBroker("/topic", "/queue");
        // 클라이언트 → 서버 메시지의 접두사
        registry.setApplicationDestinationPrefixes("/app");
        // 특정 사용자에게만 보내는 목적지의 접두사 (/user/queue/status 등)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 순서: 사용자 식별(CONNECT) 먼저, 그다음 구독 인가(SUBSCRIBE) — 서로 다른 프레임에 반응하므로
        // 실행 순서 자체는 결과에 영향이 없지만, 읽는 사람이 자연스러운 순서로 둔다
        List<ChannelInterceptor> interceptors =
                new ArrayList<>(List.of(chatSubscriptionInterceptor));
        devStompUserInterceptor.ifAvailable(interceptor -> interceptors.add(0, interceptor));
        registration.interceptors(interceptors.toArray(ChannelInterceptor[]::new));
    }
}
