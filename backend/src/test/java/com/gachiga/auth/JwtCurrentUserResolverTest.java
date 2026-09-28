package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** {@link JwtCurrentUserResolver} 검증. DB·서블릿 컨테이너 없이 순수 로직만 본다 (CLAUDE.md §7). */
class JwtCurrentUserResolverTest {

    private final JwtCurrentUserResolver resolver = new JwtCurrentUserResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 userId를 그대로 돌려준다")
    void resolvesAuthenticatedUserId() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        Object result = resolver.resolveArgument(null, null, null, null);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("인증 정보가 없으면 UNAUTHORIZED")
    void throwsWhenNoAuthentication() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
