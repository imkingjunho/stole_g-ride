package com.gachiga.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ⚠️ <b>Phase 0 스텁</b> — STOMP 연결과 구독만 되게 열어 둔다 (PRD §14.7).
 *
 * <p>주소 규약은 PRD §14.5 그대로다.
 *
 * <ul>
 *   <li>연결: {@code ws://localhost:8080/ws} (SockJS 미사용)
 *   <li>구독: {@code /user/queue/status}(내 대기 상태), {@code /user/queue/match}(매칭 알림),
 *       {@code /topic/chat/{groupId}}(채팅)
 *   <li>발신: {@code /app/chat/{groupId}}
 * </ul>
 *
 * <p>지금은 <b>인증도 없고 서버가 보내는 메시지도 없다.</b> 프론트가 연결·구독 코드를 미리 맞춰 볼 수
 * 있게 하는 것이 전부다.
 *
 * <p><b>교체 담당: 임승현 · Phase 1.</b> CONNECT 프레임의 {@code Authorization: Bearer} 검증,
 * 대기 상태·매칭 알림 push, 채팅 저장·마스킹을 붙인다. 구독 인가는
 * {@code contract.matching.MatchHistoryPort} 로 확인한다.
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

    public WebSocketConfig(@Value("${gachiga.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
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
}
