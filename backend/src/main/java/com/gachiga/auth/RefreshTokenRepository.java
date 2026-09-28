package com.gachiga.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link RefreshToken} 조회·저장.
 *
 * <p>재발급(T1-4)이 해시로 조회해 회전한다. 로그아웃(T2-5)은 사용자의 살아있는 토큰을 전부 조회해
 * 폐기한다 — 기기 하나만 쓴다고 가정하지 않는다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);
}
