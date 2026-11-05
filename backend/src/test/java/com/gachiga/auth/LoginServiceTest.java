package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.gachiga.auth.dto.LoginRequest;
import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserAccountPort;
import com.gachiga.contract.user.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link LoginService} 검증. DB 없이 순수 로직만 본다 (CLAUDE.md §7). */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final LoginRequest REQUEST = new LoginRequest("gachiga@jnu.ac.kr", "password1234");

    @Mock private UserAccountPort userAccountPort;
    @Mock private TokenService tokenService;

    private LoginService loginService;

    @Test
    @DisplayName("이메일·비밀번호가 맞으면 토큰을 발급한다")
    void loginsWithValidCredentials() {
        loginService = new LoginService(userAccountPort, tokenService);
        AccountProfile account =
                new AccountProfile(
                        1L, "gachiga@jnu.ac.kr", "후문호랑이", Gender.M, null, null, UserStatus.ACTIVE, null);
        TokenResponse tokens =
                new TokenResponse("access", "refresh", 1800, com.gachiga.auth.dto.AuthUserProfile.of(account));
        given(userAccountPort.authenticate("gachiga@jnu.ac.kr", "password1234"))
                .willReturn(Optional.of(account));
        given(tokenService.issueTokens(account)).willReturn(tokens);

        TokenResponse result = loginService.login(REQUEST);

        assertThat(result).isEqualTo(tokens);
    }

    @Test
    @DisplayName("계정을 못 찾거나 비밀번호가 틀리면 UNAUTHORIZED — 구분해 주지 않는다")
    void rejectsInvalidCredentials() {
        loginService = new LoginService(userAccountPort, tokenService);
        given(userAccountPort.authenticate("gachiga@jnu.ac.kr", "password1234"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("이용 제한 계정은 비밀번호가 맞아도 USER_SUSPENDED")
    void rejectsSuspendedAccount() {
        loginService = new LoginService(userAccountPort, tokenService);
        AccountProfile suspended =
                new AccountProfile(
                        1L,
                        "gachiga@jnu.ac.kr",
                        "후문호랑이",
                        Gender.M,
                        null,
                        null,
                        UserStatus.SUSPENDED,
                        LocalDateTime.of(2026, 10, 5, 0, 0));
        given(userAccountPort.authenticate("gachiga@jnu.ac.kr", "password1234"))
                .willReturn(Optional.of(suspended));

        assertThatThrownBy(() -> loginService.login(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_SUSPENDED);
    }
}
