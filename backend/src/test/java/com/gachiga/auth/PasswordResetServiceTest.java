package com.gachiga.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gachiga.auth.dto.PasswordResetRequest;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.contract.user.UserAccountPort;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

class PasswordResetServiceTest {
    final PasswordResetCodeStore codes = mock(PasswordResetCodeStore.class);
    final UserAccountPort accounts = mock(UserAccountPort.class);
    final JavaMailSender mail = mock(JavaMailSender.class);
    final RefreshService refresh = mock(RefreshService.class);
    final PasswordResetService service =
            new PasswordResetService(
                    codes,
                    accounts,
                    mail,
                    refresh,
                    new AuthProperties(
                            "jnu.ac.kr", 10, 5, 30, "unit-test-only-secret-long-enough", 30, 14));
    final String email = "reset-test@jnu.ac.kr";

    @Test
    void successfulResetRevokesRefreshAndCanonicalizesEmail() {
        when(codes.consume(email, "123456")).thenReturn(1L);
        when(accounts.resetPassword(email, "new-pass123")).thenReturn(Optional.of(3L));
        service.reset(new PasswordResetRequest("RESET-TEST@JNU.AC.KR", "123456", "new-pass123"));
        verify(refresh).logout(3L);
    }

    @Test
    void wrongExpiredConsumedOrLockedCodeNeverChangesPassword() {
        for (long result : new long[] {0, -1, -2}) {
            when(codes.consume(email, "123456")).thenReturn(result);
            assertThatThrownBy(
                            () ->
                                    service.reset(
                                            new PasswordResetRequest(
                                                    email, "123456", "new-pass123")))
                    .isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(accounts, refresh);
    }

    @Test
    void bcryptByteLimitCheckedBeforeConsumingCode() {
        assertThatThrownBy(
                        () ->
                                service.reset(
                                        new PasswordResetRequest(email, "123456", "한".repeat(25))))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(codes, accounts, refresh);
    }

    @Test
    void resendIsLimitedAndMailFailureInvalidatesCode() {
        when(codes.reserveSend(email)).thenReturn(false);
        assertThatThrownBy(() -> service.request(email)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mail);
        when(codes.reserveSend(email)).thenReturn(true);
        doThrow(new MailSendException("테스트 발송 실패")).when(mail).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.request(email)).isInstanceOf(BusinessException.class);
        verify(codes).clearCode(email);
    }

    @Test
    void requestDoesNotRevealAccountExistence() {
        when(codes.reserveSend(email)).thenReturn(true);
        service.request(email);
        verifyNoInteractions(accounts);
        verify(mail).send(any(SimpleMailMessage.class));
    }
}
