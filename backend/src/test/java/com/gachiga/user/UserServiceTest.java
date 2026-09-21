package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.Gender;
import com.gachiga.user.dto.UpdateProfileRequest;
import com.gachiga.user.dto.UserProfileResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link UserService} 검증. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 21, 0);

    @Mock private UserRepository userRepository;

    private UserService userService;

    @Test
    @DisplayName("있으면 프로필을 그대로 옮겨 준다")
    void returnsProfile() {
        userService = new UserService(userRepository);
        User user =
                User.create(
                        "gachiga@jnu.ac.kr", "{bcrypt}hash", "후문호랑이", Gender.M, "컴퓨터공학과", 3, NOW);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserProfileResponse profile = userService.myProfile(1L);

        assertThat(profile.email()).isEqualTo("gachiga@jnu.ac.kr");
        assertThat(profile.nickname()).isEqualTo("후문호랑이");
        assertThat(profile.department()).isEqualTo("컴퓨터공학과");
        assertThat(profile.grade()).isEqualTo(3);
        assertThat(profile.suspendedUntil()).isNull();
    }

    @Test
    @DisplayName("없으면 NOT_FOUND")
    void throwsWhenMissing() {
        userService = new UserService(userRepository);
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.myProfile(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Nested
    @DisplayName("프로필 수정 (T2-5)")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임이 비어 있으면(=본인) 바꾸고 새 프로필을 돌려준다")
        void updatesProfile() {
            userService = new UserService(userRepository);
            User user =
                    User.create(
                            "gachiga@jnu.ac.kr",
                            "{bcrypt}hash",
                            "후문호랑이",
                            Gender.M,
                            "컴퓨터공학과",
                            3,
                            NOW);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByNicknameAndIdNot("용봉동다람쥐", 1L)).willReturn(false);

            UserProfileResponse profile =
                    userService.updateProfile(
                            1L, new UpdateProfileRequest("용봉동다람쥐", "전자공학과", 4));

            assertThat(profile.nickname()).isEqualTo("용봉동다람쥐");
            assertThat(profile.department()).isEqualTo("전자공학과");
            assertThat(profile.grade()).isEqualTo(4);
            assertThat(profile.gender()).isEqualTo(Gender.M);
        }

        @Test
        @DisplayName("다른 사람이 쓰는 닉네임이면 INVALID_INPUT")
        void rejectsDuplicateNickname() {
            userService = new UserService(userRepository);
            User user =
                    User.create(
                            "gachiga@jnu.ac.kr", "{bcrypt}hash", "후문호랑이", Gender.M, null, null, NOW);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByNicknameAndIdNot("용봉동다람쥐", 1L)).willReturn(true);

            assertThatThrownBy(
                            () ->
                                    userService.updateProfile(
                                            1L, new UpdateProfileRequest("용봉동다람쥐", null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("없는 사용자면 NOT_FOUND")
        void rejectsMissingUser() {
            userService = new UserService(userRepository);
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    userService.updateProfile(
                                            99L, new UpdateProfileRequest("용봉동다람쥐", null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }
    }
}
