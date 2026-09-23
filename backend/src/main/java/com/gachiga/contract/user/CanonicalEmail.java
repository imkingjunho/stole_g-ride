package com.gachiga.contract.user;

import java.util.Locale;

/**
 * 이메일의 <b>정식 형태</b> — 앞뒤 공백을 버리고 {@link Locale#ROOT} 소문자.
 *
 * <p>규칙을 산문으로만 적어 두면 {@code auth}(Redis 키 verify:{email}·verify:fail:{email})와 {@code user}
 * (저장·비교)가 각자 구현하다 어긋난다. 어긋나면 대소문자만 바꿔 5회 잠금(FR-01)을 피하거나, 중복 검사는
 * 통과했는데 UNIQUE 제약에 걸리는 상태가 생긴다. 그래서 함수 하나를 계약에 두고 양쪽이 이것만 쓴다.
 *
 * <p>{@link UserPort#existsByEmail}·{@link UserAccountPort#register}·{@link UserAccountPort#authenticate} 의
 * 이메일과 {@code auth} 가 이메일로 만드는 모든 키·응답값은 이 함수의 결과여야 한다.
 */
public final class CanonicalEmail {

    private CanonicalEmail() {}

    /**
     * @param raw 사용자가 입력한 이메일. null 이거나 공백뿐이면 null 을 돌려준다
     * @return 정식 형태. 예: {@code "  Hong@JNU.ac.kr "} → {@code "hong@jnu.ac.kr"}
     */
    public static String of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
