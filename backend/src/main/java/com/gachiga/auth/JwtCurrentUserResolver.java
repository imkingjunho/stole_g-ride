package com.gachiga.auth;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.auth.CurrentUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser} 파라미터에 넣을 사용자 id 를 {@code SecurityContext} 에서 꺼낸다 (T1-6).
 *
 * <p>{@link JwtAuthenticationFilter} 가 principal 자리에 userId({@code Long})를 직접 심어 두므로
 * 여기서는 그대로 꺼내기만 한다. {@code DevCurrentUserResolver}(Phase 0 스텁, {@code X-Dev-User}
 * 헤더)를 대체한다 — 이 순간부터 전원의 API가 진짜 토큰을 요구한다.
 *
 * <p>이 리졸버가 불릴 때는 이미 {@code SecurityConfig} 의 {@code anyRequest().authenticated()} 를
 * 통과한 뒤라 인증이 없을 수 없지만, 방어적으로 한 번 더 확인한다 — 없으면 {@code UNAUTHORIZED}.
 */
@Component
public class JwtCurrentUserResolver implements HandlerMethodArgumentResolver {

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

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
