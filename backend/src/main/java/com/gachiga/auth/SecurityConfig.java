package com.gachiga.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 실제 인증 설정 (FR-02, T1-5). {@code DevSecurityConfig}(Phase 0 스텁)를 대체한다.
 *
 * <p>{@code /api/auth/signup}·{@code verify}·{@code login}·{@code refresh} 4개만 공개다(api-spec
 * {@code security: []}). 그 밖의 모든 REST 는 {@code Authorization: Bearer} 를 요구한다.
 *
 * <p>{@code /ws} 는 HTTP 핸드셰이크 단계에서는 permitAll 이다 — STOMP 프로토콜 자체의 인증은
 * {@code realtime/DevStompUserInterceptor}(CONNECT 프레임)가 따로 맡는다. 진짜 JWT 검증으로
 * 바꾸려면 {@code realtime} 이 이 모듈의 {@link JwtTokenProvider} 를 검증할 방법이 필요한데,
 * ArchUnit 이 도메인 간 직접 import 를 막으므로 {@code contract.auth} 에 검증용 port 가 있어야
 * 한다 — 아직 없어서 Dev 스텁이 남아 있다({@code [계약 변경 요청]} 대기).
 *
 * <p>인증 실패는 {@code common} 의 {@link ErrorCode} 를 쓴 {@link ApiResponse} 형태로 나간다
 * (CLAUDE.md §5) — Spring Security 기본 401 은 빈 본문이라 프론트의 공통 에러 처리와 안 맞는다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
        "/api/auth/signup", "/api/auth/verify", "/api/auth/login", "/api/auth/refresh"
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // JwtAuthenticationFilter 는 빈이 아니다 — 여기서 직접 만든다(그 클래스 설명 참고)
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, objectMapper);

        return http
                // 토큰 기반이라 쿠키 세션을 쓰지 않는다. CSRF 방어도 필요 없다
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CORS 설정 자체는 config/CorsConfig 가 빈으로 제공한다
                .cors(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_AUTH_ENDPOINTS)
                                        .permitAll()
                                        .requestMatchers(
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        // STOMP CONNECT 프레임에서 따로 인증한다(위 클래스 설명 참고)
                                        .requestMatchers("/ws/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 보호된 엔드포인트에 토큰 없이(또는 인증 실패 뒤 컨텍스트가 비어) 들어온 경우 */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            writeError(response, ErrorCode.UNAUTHORIZED);
        };
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
