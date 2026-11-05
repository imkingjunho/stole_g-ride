package com.gachiga.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer} 헤더의 JWT 를 검증해 {@code SecurityContext} 에 심는다 (FR-02, T1-5).
 *
 * <p>두 갈래로 나뉜다.
 *
 * <ul>
 *   <li>헤더가 아예 없음 — 그냥 다음 필터로 넘긴다. 공개 엔드포인트면 그대로 통과하고, 보호된
 *       엔드포인트면 {@code SecurityConfig} 의 인가 규칙이 막아 {@code UNAUTHORIZED} 로 응답한다
 *   <li>헤더는 있는데 검증에 실패(위조·만료) — 여기서 바로 {@code INVALID_TOKEN} 으로 끊는다.
 *       프론트는 이 코드를 보고 refresh 를 시도한다(E-09) — {@code UNAUTHORIZED} 와 구분해야 하는
 *       이유다
 * </ul>
 *
 * <p>인증된 사용자는 principal 자리에 <b>userId({@code Long})</b> 를 직접 담는다 — User 엔티티를
 * 싣지 않는다({@link JwtCurrentUserResolver} 참고).
 *
 * <p><b>{@code @Component} 로 스캔되는 빈이 아니다.</b> {@code SecurityConfig} 가 직접 {@code new} 로
 * 만들어 필터 체인에 끼워 넣는다. {@code Filter} 를 스프링 빈으로 등록하면 {@code @WebMvcTest} 가
 * (전혀 관련 없는 컨트롤러를 검사하는 테스트라도) 이 클래스를 자동으로 스캔해 생성하려 든다 — 그때
 * {@code AuthProperties}(전체 앱 컨텍스트의 {@code @ConfigurationPropertiesScan} 으로만 등록됨)를
 * 찾지 못해 팀 전원의 컨트롤러 슬라이스 테스트가 깨진다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        Optional<Long> userId = jwtTokenProvider.verify(token);
        if (userId.isEmpty()) {
            respondInvalidToken(response);
            return;
        }

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId.get(), null, List.of()));
        filterChain.doFilter(request, response);
    }

    private void respondInvalidToken(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(ErrorCode.INVALID_TOKEN));
    }
}
