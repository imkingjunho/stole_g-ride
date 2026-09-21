package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.auth.dto.SignupRequest;
import com.gachiga.auth.dto.SignupResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SignupService} 검증. DB·Redis·메일 서버 없이 도는 순수 로직 테스트다 (CLAUDE.md §7).
 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    private static final AuthProperties PROPERTIES = new AuthProperties("jnu.ac.kr", 10, 5, 30);

    @Mock private VerificationCodeStore codeStore;
    @Mock private VerificationMailSender mailSender;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(PROPERTIES, codeStore, mailSender);
    }

    @Nested
    @DisplayName("코드 발송")
    class RequestCode {

        @Test
        @DisplayName("허용 도메인이면 코드를 저장하고 메일을 보낸다")
        void sendsCodeForAllowedDomain() {
            given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(false);

            SignupResponse response =
                    signupService.requestCode(new SignupRequest("gachiga@jnu.ac.kr"));

            assertThat(response.email()).isEqualTo("gachiga@jnu.ac.kr");
            assertThat(response.expiresInSeconds()).isEqualTo(600);
            verify(codeStore).save(eq("gachiga@jnu.ac.kr"), matches("\\d{6}"));
            verify(mailSender).send(eq("gachiga@jnu.ac.kr"), matches("\\d{6}"), eq(10));
        }

        @Test
        @DisplayName("전남대 웹메일이 아니면 거절한다")
        void rejectsOtherDomain() {
            assertThatThrownBy(
                            () -> signupService.requestCode(new SignupRequest("gachiga@gmail.com")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);

            verify(codeStore, never()).save(any(), any());
        }

        @Test
        @DisplayName("@ 가 없는 형식이어도 도메인 오류로 거절한다")
        void rejectsMissingAt() {
            assertThatThrownBy(() -> signupService.requestCode(new SignupRequest("not-an-email")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }

        @Test
        @DisplayName("잠긴 계정이면 코드를 다시 보내지 않는다")
        void rejectsWhenLocked() {
            given(codeStore.isLocked("gachiga@jnu.ac.kr")).willReturn(true);

            assertThatThrownBy(
                            () ->
                                    signupService.requestCode(
                                            new SignupRequest("gachiga@jnu.ac.kr")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VERIFY_LOCKED);

            verify(mailSender, never()).send(any(), any(), anyInt());
        }

        @Test
        @DisplayName("도메인은 대소문자를 가리지 않는다")
        void domainIsCaseInsensitive() {
            given(codeStore.isLocked("gachiga@JNU.AC.KR")).willReturn(false);

            SignupResponse response =
                    signupService.requestCode(new SignupRequest("gachiga@JNU.AC.KR"));

            assertThat(response.email()).isEqualTo("gachiga@JNU.AC.KR");
        }
    }
}
