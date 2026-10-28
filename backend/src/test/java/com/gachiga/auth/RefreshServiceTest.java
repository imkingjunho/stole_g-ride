package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.auth.dto.RefreshRequest;
import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserAccountPort;
import com.gachiga.contract.user.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link RefreshService} 검증. DB 없이 순수 로직만 본다 (CLAUDE.md §7). */
@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserAccountPort userAccountPort;
    @Mock private TokenService tokenService;

    private RefreshService refreshService;

    @Nested
    @DisplayName("refresh — 회전")
    class Refresh {

        @Test
        @DisplayName("유효한 토큰이면 기존 토큰을 폐기하고 새 토큰을 발급한다")
        void rotatesValidToken() {
            refreshService = new RefreshService(refreshTokenRepository, userAccountPort, tokenService);
            String rawToken = "raw-refresh-token";
            RefreshToken stored =
                    RefreshToken.issue(1L, TokenService.hash(rawToken), NOW.plusDays(10), NOW);
            AccountProfile account =
                    new AccountProfile(
                            1L, "gachiga@jnu.ac.kr", "후문호랑이", Gender.M, null, null, UserStatus.ACTIVE, null);
            TokenResponse newTokens =
                    new TokenResponse(
                            "new-access",
                            "new-refresh",
                            1800,
                            com.gachiga.auth.dto.AuthUserProfile.of(account));
            given(refreshTokenRepository.findByTokenHash(TokenService.hash(rawToken)))
                    .willReturn(Optional.of(stored));
            given(userAccountPort.findAccountById(1L)).willReturn(Optional.of(account));
            given(tokenService.issueTokens(account)).willReturn(newTokens);

            TokenResponse result = refreshService.refresh(new RefreshRequest(rawToken));

            assertThat(result).isEqualTo(newTokens);
            assertThat(stored.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("모르는 토큰이면 INVALID_TOKEN")
        void rejectsUnknownToken() {
            refreshService = new RefreshService(refreshTokenRepository, userAccountPort, tokenService);
            given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest("unknown")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);

            verify(tokenService, never()).issueTokens(any());
        }

        @Test
        @DisplayName("이미 회전(폐기)된 토큰이면 INVALID_TOKEN — 재사용 방지")
        void rejectsRevokedToken() {
            refreshService = new RefreshService(refreshTokenRepository, userAccountPort, tokenService);
            String rawToken = "raw-refresh-token";
            RefreshToken revoked = RefreshToken.issue(1L, TokenService.hash(rawToken), NOW.plusDays(10), NOW);
            revoked.revoke();
            given(refreshTokenRepository.findByTokenHash(TokenService.hash(rawToken)))
                    .willReturn(Optional.of(revoked));

            assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(rawToken)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("만료된 토큰이면 INVALID_TOKEN")
        void rejectsExpiredToken() {
            refreshService = new RefreshService(refreshTokenRepository, userAccountPort, tokenService);
            String rawToken = "raw-refresh-token";
            RefreshToken expired =
                    RefreshToken.issue(1L, TokenService.hash(rawToken), NOW.minusDays(1), NOW.minusDays(15));
            given(refreshTokenRepository.findByTokenHash(TokenService.hash(rawToken)))
                    .willReturn(Optional.of(expired));

            assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(rawToken)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("토큰은 유효한데 계정을 못 찾으면 INVALID_TOKEN")
        void rejectsWhenAccountMissing() {
            refreshService = new RefreshService(refreshTokenRepository, userAccountPort, tokenService);
            String rawToken = "raw-refresh-token";
            RefreshToken stored =
                    RefreshToken.issue(1L, TokenService.hash(rawToken), NOW.plusDays(10), NOW);
            given(refreshTokenRepository.findByTokenHash(TokenService.hash(rawToken)))
                    .willReturn(Optional.of(stored));
            given(userAccountPort.findAccountById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(rawToken)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("이 사용자의 살아있는 토큰을 전부 폐기한다")
        void revokesAllActiveTokens() {
            refreshService = new RefreshService(refreshTokenRepository, userAccountPort, tokenService);
            RefreshToken token1 = RefreshToken.issue(1L, "hash1", NOW.plusDays(10), NOW);
            RefreshToken token2 = RefreshToken.issue(1L, "hash2", NOW.plusDays(10), NOW);
            given(refreshTokenRepository.findAllByUserIdAndRevokedFalse(1L))
                    .willReturn(List.of(token1, token2));

            refreshService.logout(1L);

            assertThat(token1.isRevoked()).isTrue();
            assertThat(token2.isRevoked()).isTrue();
        }
    }
}
