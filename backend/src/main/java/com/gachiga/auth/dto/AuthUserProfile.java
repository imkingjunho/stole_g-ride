package com.gachiga.auth.dto;

import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserStatus;
import java.time.LocalDateTime;

/**
 * 가입·로그인 응답에 담기는 계정 정보. {@code docs/api-spec.yaml} 의 {@code UserProfile} 과 1:1이다.
 *
 * <p>{@link com.gachiga.contract.user.AccountProfile} 을 그대로 반환하지 않고 이 DTO로 옮겨 담는다 —
 * 계약 타입은 모듈 간 통신용이지 API 응답 모양의 근거가 아니다. 둘이 지금 같은 모양이어도 각자 바뀌는
 * 이유가 다를 수 있다.
 */
public record AuthUserProfile(
        Long id,
        String email,
        String nickname,
        Gender gender,
        String department,
        Integer grade,
        UserStatus status,
        LocalDateTime suspendedUntil) {

    public static AuthUserProfile of(AccountProfile account) {
        return new AuthUserProfile(
                account.id(),
                account.email(),
                account.nickname(),
                account.gender(),
                account.department(),
                account.grade(),
                account.status(),
                account.suspendedUntil());
    }
}
