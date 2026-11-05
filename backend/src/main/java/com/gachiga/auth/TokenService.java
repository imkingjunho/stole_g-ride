package com.gachiga.auth;

import com.gachiga.auth.dto.AuthUserProfile;
import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.contract.user.AccountProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access·Refresh 토큰을 함께 발급한다 (FR-02, T1-3·T1-4).
 *
 * <p>가입 직후(T1-3)와 로그인(T1-4)이 이 서비스를 공유한다 — 발급 방식이 같기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties authProperties;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public TokenResponse issueTokens(AccountProfile account) {
        String accessToken = jwtTokenProvider.createAccessToken(account.id());
        String refreshToken = issueRefreshToken(account.id());

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.accessTokenExpiresInSeconds(),
                AuthUserProfile.of(account));
    }

    private String issueRefreshToken(Long userId) {
        String rawToken = generateOpaqueToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(authProperties.refreshTokenDays());

        refreshTokenRepository.save(RefreshToken.issue(userId, hash(rawToken), expiresAt, now));
        return rawToken;
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** DB에는 원문이 아니라 이 해시만 남는다 — 재발급(T1-4)·로그아웃(T2-5) 조회도 같은 해시를 쓴다 */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
