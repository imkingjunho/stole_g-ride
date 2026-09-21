package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.Gender;
import com.gachiga.user.dto.UserProfileResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
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
}
