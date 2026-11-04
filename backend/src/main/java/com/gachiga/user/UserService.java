package com.gachiga.user;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.user.dto.UpdateProfileRequest;
import com.gachiga.user.dto.UserProfileResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 프로필 조회·수정 (FR-03, T1-7·T2-5). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse myProfile(Long userId) {
        return UserProfileResponse.of(findUser(userId), LocalDateTime.now());
    }

    /**
     * 닉네임·학과·학년을 바꾼다. 성별은 바꿀 수 없다 (T2-5, FR-03). {@code nickname} 이 없으면(=
     * {@code null}) 중복 검사 없이 기존 값을 그대로 둔다 — api-spec의 {@code UpdateProfileRequest} 는
     * 세 필드 모두 선택이다.
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        String nickname = request.nickname();
        if (nickname != null) {
            requireNicknameAvailable(userId, nickname);
        }

        user.updateProfile(nickname, request.department(), request.grade());
        return UserProfileResponse.of(user, LocalDateTime.now());
    }

    private User findUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void requireNicknameAvailable(Long userId, String nickname) {
        if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 사용 중인 닉네임입니다.");
        }
    }
}
