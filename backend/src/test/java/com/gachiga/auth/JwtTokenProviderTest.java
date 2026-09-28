package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link JwtTokenProvider} 검증. 서명·클레임만 보면 되므로 DB 없이 순수 로직이다. */
class JwtTokenProviderTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-bytes-long!!";

    private final AuthProperties properties = new AuthProperties("jnu.ac.kr", 10, 5, 30, SECRET, 30, 14);
    private final JwtTokenProvider provider = new JwtTokenProvider(properties);

    @Test
    @DisplayName("userId를 subject로 담은 토큰을 만들고, 같은 시크릿으로 검증된다")
    void createsVerifiableToken() {
        String token = provider.createAccessToken(42L);

        Claims claims = parse(token, SECRET);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("만료 시각은 발급 시각 + accessTokenMinutes")
    void expirySpanMatchesConfiguredMinutes() {
        String token = provider.createAccessToken(1L);

        Claims claims = parse(token, SECRET);
        long spanMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertThat(spanMillis).isEqualTo(30L * 60 * 1000);
    }

    @Test
    @DisplayName("accessTokenExpiresInSeconds는 설정된 분을 초로 바꾼 값")
    void exposesExpiresInSeconds() {
        assertThat(provider.accessTokenExpiresInSeconds()).isEqualTo(30 * 60);
    }

    @Test
    @DisplayName("다른 시크릿으로 서명 검증하면 실패한다")
    void rejectsTokenSignedWithDifferentSecret() {
        String token = provider.createAccessToken(1L);
        String otherSecret = "different-secret-also-at-least-32-bytes!!";

        assertThatThrownBy(() -> parse(token, otherSecret)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("JWT_SECRET이 비어 있으면 발급 시점에 실패한다")
    void failsFastWhenSecretMissing() {
        AuthProperties noSecret = new AuthProperties("jnu.ac.kr", 10, 5, 30, "", 30, 14);
        JwtTokenProvider noSecretProvider = new JwtTokenProvider(noSecret);

        assertThatThrownBy(() -> noSecretProvider.createAccessToken(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    private Claims parse(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
