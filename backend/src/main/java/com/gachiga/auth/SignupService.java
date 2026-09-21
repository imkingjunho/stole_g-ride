package com.gachiga.auth;

import com.gachiga.auth.dto.SignupRequest;
import com.gachiga.auth.dto.SignupResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import java.security.SecureRandom;
import java.util.StringTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 웹메일 인증 코드 발송 (FR-01, T1-2).
 *
 * <p>거절되는 경우는 두 가지다.
 *
 * <ul>
 *   <li>{@code @jnu.ac.kr} 이 아님 → {@code EMAIL_DOMAIN_NOT_ALLOWED}
 *   <li>인증 실패가 쌓여 잠김 → {@code VERIFY_LOCKED}
 * </ul>
 *
 * <p><b>TODO(계약 변경 대기):</b> {@code docs/api-spec.yaml} 은 "이미 가입된 주소면 {@code
 * INVALID_INPUT}" 도 요구하지만, 그 판정에 필요한 {@code user.User} 조회를 이 모듈에서 바로 하면
 * ArchUnit 이 막는다(auth → user 직접 import 금지, CLAUDE.md §4). {@code contract.user.UserPort} 에
 * {@code existsByEmail(String)} 이 생기면 그걸로 채운다 — 요청 문구는 CLAUDE.md §10.4 참고.
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

    public SignupResponse requestCode(SignupRequest request) {
        String email = request.email().trim();

        requireAllowedDomain(email);
        requireNotLocked(email);

        String code = generateCode();
        codeStore.save(email, code);
        mailSender.send(email, code, authProperties.verifyCodeTtlMinutes());

        log.info("웹메일 인증 코드 발송 email={}", email);
        return new SignupResponse(email, authProperties.verifyCodeTtlMinutes() * 60);
    }

    private void requireAllowedDomain(String email) {
        String domain = domainOf(email);
        if (domain == null || !domain.equalsIgnoreCase(authProperties.allowedEmailDomain())) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
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
