package com.gachiga.user;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.user.dto.UpdateProfileRequest;
import com.gachiga.user.dto.UserProfileResponse;
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
        return UserProfileResponse.of(findUser(userId));
    }

    /** 닉네임·학과·학년을 바꾼다. 성별은 바꿀 수 없다 (T2-5, FR-03) */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        requireNicknameAvailable(userId, request.nickname());

        user.updateProfile(request.nickname(), request.department(), request.grade());
        return UserProfileResponse.of(user);
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
