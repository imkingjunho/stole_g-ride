package com.gachiga.auth;

import com.gachiga.auth.dto.SignupRequest;
import com.gachiga.auth.dto.SignupResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.UserPort;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.StringTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 웹메일 인증 코드 발송 (FR-01, T1-2).
 *
 * <p>거절되는 경우는 세 가지다.
 *
 * <ul>
 *   <li>{@code @jnu.ac.kr} 이 아님 → {@code EMAIL_DOMAIN_NOT_ALLOWED}
 *   <li>이미 가입된 주소 → {@code INVALID_INPUT}
 *   <li>인증 실패가 쌓여 잠김 → {@code VERIFY_LOCKED}
 * </ul>
 *
 * <p>이메일은 앞뒤 공백을 버리고 {@link Locale#ROOT} 기준 소문자로 맞춘 뒤 비교·저장·Redis 키
 * 생성에 일관되게 쓴다 ({@link UserPort#existsByEmail(String)} 계약 참고) — 대소문자만 바꿔 재요청하면
 * 중복 검사와 잠금 카운트가 서로 다른 주소를 보는 상태가 생기기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    /** 6자리 코드의 범위. {@code String.format("%06d", ...)} 로 앞자리 0 을 채운다 */
    private static final int CODE_BOUND = 1_000_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthProperties authProperties;
    private final VerificationCodeStore codeStore;
    private final VerificationMailSender mailSender;
    private final UserPort userPort;

    public SignupResponse requestCode(SignupRequest request) {
        String email = normalize(request.email());

        requireAllowedDomain(email);
        requireNotAlreadyRegistered(email);
        requireNotLocked(email);

        String code = generateCode();
        codeStore.save(email, code);
        mailSender.send(email, code, authProperties.verifyCodeTtlMinutes());

        log.info("웹메일 인증 코드 발송 email={}", email);
        return new SignupResponse(email, authProperties.verifyCodeTtlMinutes() * 60);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void requireAllowedDomain(String email) {
        String domain = domainOf(email);
        if (domain == null || !domain.equalsIgnoreCase(authProperties.allowedEmailDomain())) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }
    }

    private void requireNotAlreadyRegistered(String email) {
        if (userPort.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireNotLocked(String email) {
        if (codeStore.isLocked(email)) {
            throw new BusinessException(ErrorCode.VERIFY_LOCKED);
        }
    }

    /**
     * {@code "@"} 뒤쪽만 뗀다.
     *
     * <p>문자열 파싱은 {@link StringTokenizer} 를 쓴다 (CLAUDE.md §5). {@code @} 가 없거나 둘 이상이면
     * 형식이 잘못된 것이므로 {@code null} 을 돌려준다 — {@code @Email} 이 먼저 걸러 주지만 방어적으로 둔다.
     */
    private String domainOf(String email) {
        StringTokenizer tokenizer = new StringTokenizer(email, "@");
        if (tokenizer.countTokens() != 2) {
            return null;
        }
        tokenizer.nextToken();
        return tokenizer.nextToken();
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }
}
