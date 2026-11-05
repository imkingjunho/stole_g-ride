package com.gachiga.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 브라우저 교차 출처 요청 허용 범위.
 *
 * <p>프론트 개발 서버(Vite, {@code http://localhost:5173})와 백엔드({@code :8080})는 포트가 달라
 * 브라우저가 기본적으로 요청을 막는다. 그 예외를 여기서 연다.
 *
 * <p>허용 출처를 {@code *} 로 열지 않는다. 자격 증명(쿠키·Authorization 헤더)을 함께 보내려면
 * 출처를 정확히 지정해야 하고, 무엇보다 아무 사이트나 우리 API 를 부르게 둘 이유가 없다.
 *
 * <p>허용 출처는 {@code application.yml} 의 {@code gachiga.cors.allowed-origins} 한 곳에 있다.
 * {@code realtime/WebSocketConfig} 가 같은 값을 읽으므로, 배포 도메인은 거기 한 줄만 고치면
 * REST 와 WebSocket 이 함께 열린다 (Phase 3 T3-3).
 *
 * <p><b>주의:</b> 이 빈만 있어서는 아무 일도 일어나지 않는다. {@code SecurityFilterChain} 이
 * {@code .cors(Customizer.withDefaults())} 를 호출해야 적용된다. 인증을 실제 구현으로 바꿀 때
 * 그 한 줄을 빠뜨리면 프론트가 CORS 오류를 보게 된다.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${gachiga.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = Arrays.asList(allowedOrigins);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 토큰 재발급 등에서 쿠키·인증 헤더를 함께 보낼 수 있게 한다
        config.setAllowCredentials(true);
        // 프리플라이트(OPTIONS) 결과를 1시간 재사용해 왕복을 줄인다
        config.setMaxAge(3_600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
