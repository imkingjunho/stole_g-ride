package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link TokenService} 검증. DB 없이 리포지토리는 Mock으로 대체한다. */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final AuthProperties PROPERTIES =
            new AuthProperties("jnu.ac.kr", 10, 5, 30, "test-secret-at-least-32-bytes-long!!", 30, 14);

    private static final AccountProfile ACCOUNT =
            new AccountProfile(1L, "gachiga@jnu.ac.kr", "후문호랑이", Gender.M, null, null, UserStatus.ACTIVE, null);

    @Mock private RefreshTokenRepository refreshTokenRepository;

    private TokenService tokenService;

    @Test
    @DisplayName("access·refresh 토큰을 함께 발급하고, refresh는 해시로만 저장한다")
    void issuesBothTokens() {
        tokenService =
                new TokenService(new JwtTokenProvider(PROPERTIES), refreshTokenRepository, PROPERTIES);

        TokenResponse response = tokenService.issueTokens(ACCOUNT);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.accessTokenExpiresIn()).isEqualTo(30 * 60);
        assertThat(response.user().email()).isEqualTo("gachiga@jnu.ac.kr");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("매번 다른 refresh 토큰을 만든다")
    void generatesUniqueRefreshTokens() {
        tokenService =
                new TokenService(new JwtTokenProvider(PROPERTIES), refreshTokenRepository, PROPERTIES);
        given(refreshTokenRepository.save(any())).willReturn(null);

        TokenResponse first = tokenService.issueTokens(ACCOUNT);
        TokenResponse second = tokenService.issueTokens(ACCOUNT);

        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
    }
}
