package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserSummary;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserAdapter} 검증. {@link UserPort}를 다른 모듈이 기대하는 모양대로 돌려주는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserAdapterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 21, 0);

    @Mock private UserRepository userRepository;

    private UserAdapter adapter;

    @Test
    @DisplayName("있으면 UserSummary 로 옮겨 준다")
    void findsExistingUser() {
        adapter = new UserAdapter(userRepository);
        User user = User.create("gachiga@jnu.ac.kr", "{bcrypt}hash", "후문호랑이", Gender.M, null, null, NOW);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        Optional<UserSummary> found = adapter.findById(1L);

        assertThat(found).isPresent();
        assertThat(found.get().nickname()).isEqualTo("후문호랑이");
        assertThat(found.get().gender()).isEqualTo(Gender.M);
    }

    @Test
    @DisplayName("없으면 빈 값 — null 을 돌려주지 않는다")
    void emptyWhenMissing() {
        adapter = new UserAdapter(userRepository);
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThat(adapter.findById(99L)).isEmpty();
    }
}
