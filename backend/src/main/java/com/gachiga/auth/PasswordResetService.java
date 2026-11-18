package com.gachiga.auth;

import com.gachiga.auth.dto.PasswordResetRequest;
import com.gachiga.common.exception.*;
import com.gachiga.contract.user.*;

import lombok.RequiredArgsConstructor;

import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PasswordResetCodeStore codes;
    private final UserAccountPort accounts;
    private final JavaMailSender mail;
    private final RefreshService refresh;
    private final AuthProperties properties;

    public void request(String rawEmail) {
        String email = email(rawEmail);
        if (!codes.reserveSend(email))
            throw new BusinessException(ErrorCode.VERIFY_LOCKED, "1분 후 다시 요청해 주세요.");
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.save(email, code);
        var message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[가치가] 비밀번호 재설정 인증 코드");
        message.setText("비밀번호 재설정 인증 코드: " + code + "\n10분 안에 입력해 주세요. 직접 요청하지 않았다면 무시해 주세요.");
        // 가입 여부와 무관하게 같은 흐름. 코드·비밀번호는 로그에 남기지 않는다.
        try {
            mail.send(message);
        } catch (MailException e) {
            codes.clearCode(email);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "인증 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요.", e);
        }
    }

    @Transactional
    public void reset(PasswordResetRequest request) {
        String email = email(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        long result = codes.consume(email, request.code());
        if (result == -2)
            throw new BusinessException(
                    ErrorCode.VERIFY_LOCKED, "인증 시도 횟수를 초과했어요. 30분 뒤 다시 시도해 주세요.");
        if (result != 1) throw new BusinessException(ErrorCode.VERIFY_CODE_MISMATCH);
        Long id =
                accounts.resetPassword(email, request.password())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVALID_INPUT, "가입 정보를 확인해 주세요."));
        refresh.logout(id);
    }

    private String email(String raw) {
        String email = CanonicalEmail.of(raw);
        if (email == null || !email.endsWith("@" + properties.allowedEmailDomain()))
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        return email;
    }
}
