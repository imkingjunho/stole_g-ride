package com.gachiga.auth;

import com.gachiga.auth.dto.LoginRequest;
import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.UserAccountPort;
import com.gachiga.contract.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 로그인 (FR-02, T1-4).
 *
 * <p>이메일이 없는지, 비밀번호가 틀렸는지는 구분하지 않는다 — 둘 다 {@code UNAUTHORIZED}다({@link
 * UserAccountPort#authenticate} 계약). 이용 제한 계정은 비밀번호가 맞으면 인증은 통과하되 {@code
 * USER_SUSPENDED} 로 거절한다 — 그래야 "며칠 남았다"를 프론트가 보여줄 수 있다.
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserAccountPort userAccountPort;
    private final TokenService tokenService;

    public TokenResponse login(LoginRequest request) {
        AccountProfile account =
                userAccountPort
                        .authenticate(request.email(), request.password())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (account.status() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }

        return tokenService.issueTokens(account);
    }
}
