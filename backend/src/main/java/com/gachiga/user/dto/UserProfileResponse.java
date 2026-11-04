package com.gachiga.user.dto;

import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserStatus;
import com.gachiga.user.User;
import java.time.LocalDateTime;

/**
 * 내 프로필 응답. {@code docs/api-spec.yaml} 의 {@code UserProfile} 과 1:1 이다.
 *
 * <p>실명·전화번호는 담지 않는다 (PRD §2.2).
 */
public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        Gender gender,
        String department,
        Integer grade,
        UserStatus status,
        LocalDateTime suspendedUntil) {

    /**
     * {@code now} 기준으로 계산한 상태를 담는다 ({@link User#currentStatus}) — 저장값을 그대로 쓰면
     * 정지 기간이 지난 사용자의 프로필이 로그인 응답({@code UserAccountAdapter})·{@code UserPort}
     * 와 다르게 보인다.
     */
    public static UserProfileResponse of(User user, LocalDateTime now) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getGender(),
                user.getDepartment(),
                user.getGrade(),
                user.currentStatus(now),
                user.currentSuspendedUntil(now));
    }
}
