package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.auth.dto.VerifyRequest;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.NewAccount;
import com.gachiga.contract.user.UserAccountPort;
import com.gachiga.contract.user.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link VerifyService} 검증. DB·Redis 없이 도는 순수 로직 테스트다 (CLAUDE.md §7). */
@ExtendWith(MockitoExtension.class)
class VerifyServiceTest {

    private static final AccountProfile ACCOUNT =
            new AccountProfile(1L, "gachiga@jnu.ac.kr", "후문호랑이", Gender.M, null, null, UserStatus.ACTIVE, null);

    private static final TokenResponse TOKENS =
            new TokenResponse("access", "refresh", 1800, com.gachiga.auth.dto.AuthUserProfile.of(ACCOUNT));

    @Mock private VerificationCodeStore codeStore;
    @Mock private UserAccountPort userAccountPort;
    @Mock private TokenService tokenService;

    private VerifyService verifyService;

    private final VerifyRequest request =
            new VerifyRequest(
                    "gachiga@jnu.ac.kr", "482913", "password1234", "후문호랑이", Gender.M, "컴퓨터공학과", 3);

    @Test
    @DisplayName("코드가 맞으면 계정을 만들고 토큰을 돌려준 뒤 코드를 지운다")
    void verifiesAndRegisters() {
        verifyService = new VerifyService(codeStore, userAccountPort, tokenService);
        given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(false);
        given(codeStore.find("gachiga@jnu.ac.kr")).willReturn(Optional.of("482913"));
        given(userAccountPort.register(any(NewAccount.class))).willReturn(ACCOUNT);
        given(tokenService.issueTokens(ACCOUNT)).willReturn(TOKENS);

        TokenResponse result = verifyService.verify(request);

        assertThat(result).isEqualTo(TOKENS);
        verify(codeStore).clear("gachiga@jnu.ac.kr");
        verify(codeStore, never()).recordFailure(any());

        ArgumentCaptor<NewAccount> captor = ArgumentCaptor.forClass(NewAccount.class);
        verify(userAccountPort).register(captor.capture());
        NewAccount account = captor.getValue();
        assertThat(account.email()).isEqualTo("gachiga@jnu.ac.kr");
        assertThat(account.rawPassword()).isEqualTo("password1234");
        assertThat(account.nickname()).isEqualTo("후문호랑이");
        assertThat(account.department()).isEqualTo("컴퓨터공학과");
        assertThat(account.grade()).isEqualTo(3);
    }

    @Test
    @DisplayName("이메일 대소문자가 섞여 있어도 정규화해서 코드 저장소를 조회한다")
    void normalizesEmailBeforeLookup() {
        verifyService = new VerifyService(codeStore, userAccountPort, tokenService);
        VerifyRequest upperCaseEmail =
                new VerifyRequest(
                        "Gachiga@JNU.AC.KR", "482913", "password1234", "후문호랑이", Gender.M, null, null);
        given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(false);
        given(codeStore.find("gachiga@jnu.ac.kr")).willReturn(Optional.of("482913"));
        given(userAccountPort.register(any(NewAccount.class))).willReturn(ACCOUNT);
        given(tokenService.issueTokens(ACCOUNT)).willReturn(TOKENS);

        verifyService.verify(upperCaseEmail);

        verify(codeStore).isLocked("gachiga@jnu.ac.kr");
        verify(codeStore).find("gachiga@jnu.ac.kr");
    }

    @Test
    @DisplayName("잠긴 계정이면 코드를 검사하지 않고 바로 거절한다")
    void rejectsWhenLocked() {
        verifyService = new VerifyService(codeStore, userAccountPort, tokenService);
        given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(true);

        assertThatThrownBy(() -> verifyService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFY_LOCKED);

        verify(codeStore, never()).find(any());
        verify(userAccountPort, never()).register(any());
    }

    @Test
    @DisplayName("코드가 틀리면 실패를 기록하고 거절한다")
    void rejectsWrongCode() {
        verifyService = new VerifyService(codeStore, userAccountPort, tokenService);
        given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(false);
        given(codeStore.find("gachiga@jnu.ac.kr")).willReturn(Optional.of("000000"));

        assertThatThrownBy(() -> verifyService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFY_CODE_MISMATCH);

        verify(codeStore).recordFailure("gachiga@jnu.ac.kr");
        verify(userAccountPort, never()).register(any());
    }

    @Test
    @DisplayName("코드가 만료(없음)됐으면 실패를 기록하고 거절한다")
    void rejectsExpiredCode() {
        verifyService = new VerifyService(codeStore, userAccountPort, tokenService);
        given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(false);
        given(codeStore.find("gachiga@jnu.ac.kr")).willReturn(Optional.empty());

        assertThatThrownBy(() -> verifyService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFY_CODE_MISMATCH);

        verify(codeStore).recordFailure("gachiga@jnu.ac.kr");
    }

    @Test
    @DisplayName("닉네임·이메일 중복은 UserAccountPort가 던진 예외를 그대로 전파한다")
    void propagatesRegisterFailure() {
        verifyService = new VerifyService(codeStore, userAccountPort, tokenService);
        given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(false);
        given(codeStore.find("gachiga@jnu.ac.kr")).willReturn(Optional.of("482913"));
        given(userAccountPort.register(any(NewAccount.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        assertThatThrownBy(() -> verifyService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(codeStore, never()).clear(eq("gachiga@jnu.ac.kr"));
        verify(tokenService, never()).issueTokens(any());
    }
}
