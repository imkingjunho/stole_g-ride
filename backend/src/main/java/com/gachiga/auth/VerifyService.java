package com.gachiga.auth;

import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.auth.dto.VerifyRequest;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.CanonicalEmail;
import com.gachiga.contract.user.NewAccount;
import com.gachiga.contract.user.UserAccountPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 인증 코드 검증 + 가입 완료 (FR-01·FR-03, T1-3).
 *
 * <p>거절되는 경우는 세 가지다.
 *
 * <ul>
 *   <li>인증 실패가 쌓여 잠김 → {@code VERIFY_LOCKED}
 *   <li>코드가 틀리거나 만료됨 → {@code VERIFY_CODE_MISMATCH}
 *   <li>닉네임·이메일 중복(경합 포함) → {@code INVALID_INPUT} ({@link UserAccountPort#register} 가 던짐)
 * </ul>
 *
 * <p>성공하면 계정을 만들고 바로 로그인 상태로 만든다 — api-spec 의 {@code /api/auth/verify} 200
 * 응답이 {@code TokenResponse} 이기 때문이다(가입 직후 재로그인을 요구하지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyService {

    private final VerificationCodeStore codeStore;
    private final UserAccountPort userAccountPort;
    private final TokenService tokenService;

    public TokenResponse verify(VerifyRequest request) {
        String email = CanonicalEmail.of(request.email());

        requireNotLocked(email);
        requireCodeMatches(email, request.code());

        AccountProfile account =
                userAccountPort.register(
                        new NewAccount(
                                email,
                                request.password(),
                                request.nickname(),
                                request.gender(),
                                request.department(),
                                request.grade()));

        codeStore.clear(email);
        log.info("가입 완료 email={} userId={}", email, account.id());
        return tokenService.issueTokens(account);
    }

    private void requireNotLocked(String email) {
        if (codeStore.isLocked(email)) {
            throw new BusinessException(ErrorCode.VERIFY_LOCKED);
        }
    }

    private void requireCodeMatches(String email, String code) {
        Optional<String> storedCode = codeStore.find(email);
        if (storedCode.isEmpty() || !storedCode.get().equals(code)) {
            codeStore.recordFailure(email);
            throw new BusinessException(ErrorCode.VERIFY_CODE_MISMATCH);
        }
    }
}
