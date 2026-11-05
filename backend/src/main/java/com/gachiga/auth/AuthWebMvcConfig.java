package com.gachiga.auth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code auth} 모듈의 스프링 MVC 설정. {@link com.gachiga.contract.auth.CurrentUser} 리졸버를
 * 등록한다 (T1-6).
 *
 * <p>{@link JwtCurrentUserResolver} 가 모든 프로파일에서 등록된다 — {@code DevCurrentUserResolver}
 * (Phase 0 스텁, {@code X-Dev-User} 헤더)는 삭제됐다.
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final JwtCurrentUserResolver jwtCurrentUserResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(jwtCurrentUserResolver);
    }
}
