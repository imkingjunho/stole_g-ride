package com.gachiga.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Access 토큰 발급 (FR-02, T1-3·T1-4). {@code jjwt} 0.12.x API를 쓴다.
 *
 * <p>Refresh 토큰은 JWT가 아니다 — {@link TokenService} 가 만드는 불투명(opaque) 값이다. Access
 * 토큰만 서명해서 서버 상태 없이(stateless) 검증할 수 있게 하고, Refresh는 DB에서 해시로 조회해야만
 * 유효성을 알 수 있게 해서 회전·폐기가 즉시 반영되게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AuthProperties authProperties;

    private SecretKey signingKey() {
        String secret = authProperties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 이 비어 있다 — 로컬 .env 에 32자 이상의 임의 문자열을 넣어야 한다");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** userId 를 subject 로 담은 Access 토큰을 만든다 */
    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(authProperties.accessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    /** Access 토큰 발급 시점의 남은 유효 시간(초). {@code TokenResponse.accessTokenExpiresIn} 에 쓴다 */
    public int accessTokenExpiresInSeconds() {
        return authProperties.accessTokenMinutes() * 60;
    }

    /**
     * Access 토큰을 검증한다 (T1-5, {@code JwtAuthenticationFilter} 가 부른다).
     *
     * @return 서명이 맞고 만료되지 않았으면 subject 의 userId. 위조·만료·형식 오류면 빈 값 — 원인은
     *     로그에만 남기고 호출자에게는 구분해 주지 않는다(전부 같은 {@code INVALID_TOKEN})
     */
    public Optional<Long> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 검증 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
