package com.gachiga.auth;

import com.gachiga.contract.auth.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * ⚠️ <b>Phase 0 스텁</b> — {@link CurrentUser} 파라미터에 넣을 사용자 id 를 헤더에서 가져온다 (PRD §14.7).
 *
 * <p>요청 헤더 {@code X-Dev-User} 값을 쓰고, 헤더가 없거나 숫자가 아니면 {@code 1} 을 준다.
 * Swagger 나 {@code curl} 에서 헤더만 바꿔 여러 사용자를 흉내 낼 수 있다.
 *
 * <pre>{@code
 * curl -H "X-Dev-User: 2" http://localhost:8080/api/requests/me
 * }</pre>
 *
 * <p>{@code users} 테이블에 실제로 그 id 의 행이 있어야 {@code UserPort.findById} 가 값을 돌려준다 —
 * 가입 전이면 헤더로 아무 숫자나 넣어도 프로필 조회 같은 곳에서는 {@code NOT_FOUND} 가 난다.
 *
 * <p><b>교체 담당: 임승현 · Phase 1.</b> JWT 에서 사용자 id 를 꺼내는 리졸버로 바꾸고 이 클래스를 삭제한다.
 *
 * <p>{@link DevSecurityConfig} 와 마찬가지로 {@code local}·{@code test} 에서만 등록된다.
 * 헤더 하나로 남의 계정을 흉내 낼 수 있는 기능이 운영에 남으면 안 되기 때문이다.
 */
@Slf4j
@Component
@Profile({"local", "test"})
public class DevCurrentUserResolver implements HandlerMethodArgumentResolver {

    /** 헤더가 없을 때 쓰는 기본 사용자 */
    private static final Long DEFAULT_USER_ID = 1L;

    private static final String DEV_USER_HEADER = "X-Dev-User";

    /** {@code @CurrentUser} 가 붙은 {@code Long} 파라미터만 처리한다 */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        String header = webRequest.getHeader(DEV_USER_HEADER);
        if (header == null || header.isBlank()) {
            return DEFAULT_USER_ID;
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            log.warn("[DEV] {} 헤더가 숫자가 아닙니다: '{}'. {}번 사용자로 처리합니다.",
                    DEV_USER_HEADER, header, DEFAULT_USER_ID);
            return DEFAULT_USER_ID;
        }
    }
}
