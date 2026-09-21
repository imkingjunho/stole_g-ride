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

    public static UserProfileResponse of(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getGender(),
                user.getDepartment(),
                user.getGrade(),
                user.getStatus(),
                user.getSuspendedUntil());
    }
}
