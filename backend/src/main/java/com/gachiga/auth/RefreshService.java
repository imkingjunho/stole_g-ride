package com.gachiga.auth;

import com.gachiga.auth.dto.RefreshRequest;
import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.UserAccountPort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh 토큰 재발급(회전)·로그아웃 (FR-02, T1-4·T2-5).
 *
 * <p>둘 다 {@code refresh_tokens} 의 생애주기를 다룬다는 점에서 하나의 서비스로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class RefreshService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAccountPort userAccountPort;
    private final TokenService tokenService;

    /**
     * 토큰을 새 쌍으로 바꾼다(회전). 쓴 토큰은 즉시 폐기해서, 탈취된 토큰이 재사용되면 이미 회전된
     * 걸로 걸러진다.
     *
     * @throws BusinessException {@code INVALID_TOKEN} — 모르는 토큰, 이미 회전(폐기)됐거나 만료된 토큰
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token =
                refreshTokenRepository
                        .findByTokenHash(TokenService.hash(request.refreshToken()))
                        .filter(t -> t.isUsable(now))
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        token.revoke();

        AccountProfile account =
                userAccountPort
                        .findAccountById(token.getUserId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        return tokenService.issueTokens(account);
    }

    /** 이 사용자의 살아있는 Refresh 토큰을 전부 폐기한다. Access 토큰은 만료까지 그대로 유효하다 */
    @Transactional
    public void logout(Long userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId);
        activeTokens.forEach(RefreshToken::revoke);
    }
}
