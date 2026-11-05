package com.gachiga.auth;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 인증 코드를 메일로 보낸다 (FR-01).
 *
 * <p>{@code local} 프로파일에서는 코드를 로그에도 남긴다. 학교 SMTP·Gmail 앱 비밀번호 같은 메일
 * 발송 수단이 아직 없어도(Phase 0) 로그의 코드로 계속 개발할 수 있게 하기 위해서다 — 그래서 local
 * 에서는 발송이 실패해도 흐름을 막지 않는다. 그 밖의 프로파일에서는 발송 실패를 그대로 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationMailSender {

    private static final String SUBJECT = "[가치가] 이메일 인증 코드";

    private final JavaMailSender mailSender;
    private final Environment environment;

    public void send(String email, String code, int ttlMinutes) {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        if (local) {
            log.info("[local] {} 인증 코드: {} ({}분 유효)", email, code, ttlMinutes);
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(SUBJECT);
            message.setText("인증 코드: " + code + " (" + ttlMinutes + "분간 유효합니다)");
            mailSender.send(message);
        } catch (MailException e) {
            if (local) {
                log.warn(
                        "메일 발송 실패 — local 환경이라 위 로그의 코드로 계속 진행합니다. email={}",
                        email,
                        e);
                return;
            }
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "인증 코드 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }
    }
}
