package com.gachiga.auth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code auth} 모듈의 스프링 MVC 설정. 지금은 {@link com.gachiga.contract.auth.CurrentUser} 리졸버를
 * 등록하는 일만 한다.
 *
 * <p>리졸버 구현체는 Phase 1 에 바뀌지만 <b>등록하는 이 파일은 그대로 남는다.</b>
 * 임승현은 주입되는 구현만 갈아 끼우면 된다.
 *
 * <p>{@link DevCurrentUserResolver} 는 {@code local}·{@code test} 에만 있으므로 {@link ObjectProvider}
 * 로 받아 "있으면 등록"한다. 그렇게 하지 않으면 운영 프로파일에서 빈을 찾지 못해 기동이 깨진다.
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<DevCurrentUserResolver> devCurrentUserResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        devCurrentUserResolver.ifAvailable(resolvers::add);
    }
}
