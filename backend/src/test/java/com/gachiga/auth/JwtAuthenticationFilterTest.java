package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** {@link JwtAuthenticationFilter} 검증. 서블릿 컨테이너 없이 Mock 요청/응답으로 돈다 (CLAUDE.md §7). */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 그냥 다음 필터로 넘긴다")
    void passesThroughWhenNoHeader() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).verify(any());
    }

    @Test
    @DisplayName("유효한 토큰이면 SecurityContext에 userId를 심고 다음 필터로 넘긴다")
    void authenticatesValidToken() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, objectMapper);
        given(jwtTokenProvider.verify("good-token")).willReturn(Optional.of(42L));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(42L);
    }

    @Test
    @DisplayName("위조·만료된 토큰이면 다음 필터로 넘기지 않고 401 INVALID_TOKEN을 바로 응답한다")
    void rejectsInvalidTokenImmediately() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, objectMapper);
        given(jwtTokenProvider.verify("bad-token")).willReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull(); // 체인이 이어지지 않았다
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두사가 없는 헤더는 무시하고 다음 필터로 넘긴다")
    void ignoresNonBearerHeader() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(jwtTokenProvider, never()).verify(any());
    }
}
