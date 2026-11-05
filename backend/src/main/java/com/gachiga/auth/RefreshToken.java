package com.gachiga.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refresh 토큰 한 개 (PRD §7.1 {@code refresh_tokens}).
 *
 * <p>{@code userId} 는 ID만 저장한다 — {@code user} 모듈 엔티티를 JPA 관계로 참조하지 않는다
 * (CLAUDE.md §4).
 *
 * <p><b>토큰 원문은 어디에도 저장하지 않는다.</b> 해시(SHA-256)만 저장하고, 재발급 시점(회전)에 기존
 * 값을 {@code revoked}로 바꾼다 — 탈취된 refresh 토큰이 재사용되면 이미 회전된 걸 알아채고 전부
 * 무효화할 수 있는 근거가 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "refresh_tokens",
        indexes = {@Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex. 원문은 발급 시 딱 한 번 호출자에게만 돌아가고 저장하지 않는다 */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static RefreshToken issue(
            Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime now) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.revoked = false;
        token.createdAt = now;
        return token;
    }

    /** 재발급(회전) 또는 로그아웃 시 기존 토큰을 무효화한다 */
    public void revoke() {
        this.revoked = true;
    }

    public boolean isUsable(LocalDateTime now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
