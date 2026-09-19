package com.gachiga.auth;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ⚠️ <b>Phase 0 스텁</b> — 모든 요청을 그냥 통과시킨다 (PRD §14.7).
 *
 * <p>인증이 아직 없어도 나머지 네 사람이 API 를 만들고 Swagger 에서 눌러 볼 수 있게 하는 것이 목적이다.
 * 실제 사용자 구분은 {@link DevCurrentUserResolver} 가 {@code X-Dev-User} 헤더로 흉내 낸다.
 *
 * <p><b>교체 담당: 임승현 · Phase 1.</b> JWT 필터·실제 리졸버를 넣고 이 클래스를 <b>삭제</b>한다
 * (둘을 같이 두려면 진짜 쪽에 {@code @Primary} — CLAUDE.md §3).
 *
 * <p>스텁이 남아 있다는 것을 잊지 않도록 기동할 때마다 경고 로그를 남긴다 (E-11).
 *
 * <p><b>{@code local}·{@code test} 프로파일에서만 등록된다.</b> 교체를 잊은 채 운영으로 띄우면
 * 이 빈이 없어 스프링 시큐리티 기본 설정이 살아나고 모든 요청이 401 로 막힌다. 잊었을 때
 * "전부 열림"이 아니라 "전부 막힘"으로 실패하는 편이 안전하기 때문이다.
 * 프로파일을 지정하지 않으면 {@code local} 이므로(application.yml) 평소 개발에는 영향이 없다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@Profile({"local", "test"})
public class DevSecurityConfig {

    @PostConstruct
    void warnStubIsActive() {
        log.warn("=".repeat(78));
        log.warn("[DEV] 인증이 꺼져 있습니다. 모든 API 가 인증 없이 열려 있습니다.");
        log.warn("[DEV] 현재 사용자는 X-Dev-User 헤더로 정해집니다 (헤더가 없으면 1번).");
        log.warn("[DEV] auth/DevSecurityConfig — 임승현이 Phase 1 에 실제 인증으로 교체합니다.");
        log.warn("=".repeat(78));
    }

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        return http
                // 토큰 기반으로 갈 예정이라 쿠키 세션을 쓰지 않는다. CSRF 방어도 필요 없다
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CORS 설정 자체는 config/CorsConfig 가 빈으로 제공한다
                .cors(Customizer.withDefaults())
                // 로그인 화면·기본 인증 팝업을 띄우지 않는다
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
