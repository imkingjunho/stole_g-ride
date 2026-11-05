package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.NewAccount;
import com.gachiga.contract.user.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link UserAccountAdapter} 검증. DB 없이 순수 로직만 본다 — 실제 UNIQUE 제약은 {@link
 * UserRepositoryTest} 가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class UserAccountAdapterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 21, 0);

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UserAccountAdapter adapter;

    @BeforeEach
    void setUp() {
        // 생성자가 더미 해시를 만들 때 쓴다 (타이밍 공격 방지용, 실제 암호와 무관)
        given(passwordEncoder.encode(anyString())).willReturn("dummy-hash-for-test");
        adapter = new UserAccountAdapter(userRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("register")
    class Register {

        private final NewAccount account =
                new NewAccount(
                        "gachiga@jnu.ac.kr", "password1234", "후문호랑이", Gender.M, "컴퓨터공학과", 3);

        @Test
        @DisplayName("정상 가입 — 비밀번호를 해시하고 이메일은 정식 형태로 저장한다")
        void registersAccount() {
            given(userRepository.existsByEmail("gachiga@jnu.ac.kr")).willReturn(false);
            given(userRepository.existsByNickname("후문호랑이")).willReturn(false);
            given(passwordEncoder.encode("password1234")).willReturn("{bcrypt}hashed");
            given(userRepository.saveAndFlush(any(User.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            AccountProfile profile = adapter.register(account);

            assertThat(profile.email()).isEqualTo("gachiga@jnu.ac.kr");
            assertThat(profile.nickname()).isEqualTo("후문호랑이");
            assertThat(profile.gender()).isEqualTo(Gender.M);
        }

        @Test
        @DisplayName("이메일이 대문자 섞여 들어와도 소문자로 정규화해서 저장한다")
        void normalizesEmail() {
            NewAccount mixedCase =
                    new NewAccount(
                            "  Gachiga@JNU.AC.KR  ", "password1234", "후문호랑이", Gender.M, null, null);
            given(userRepository.existsByEmail("gachiga@jnu.ac.kr")).willReturn(false);
            given(userRepository.existsByNickname("후문호랑이")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("{bcrypt}hashed");
            given(userRepository.saveAndFlush(any(User.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            AccountProfile profile = adapter.register(mixedCase);

            assertThat(profile.email()).isEqualTo("gachiga@jnu.ac.kr");
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 INVALID_INPUT")
        void rejectsDuplicateEmail() {
            given(userRepository.existsByEmail("gachiga@jnu.ac.kr")).willReturn(true);

            assertThatThrownBy(() -> adapter.register(account))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);

            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("이미 쓰이는 닉네임이면 INVALID_INPUT")
        void rejectsDuplicateNickname() {
            given(userRepository.existsByEmail("gachiga@jnu.ac.kr")).willReturn(false);
            given(userRepository.existsByNickname("후문호랑이")).willReturn(true);

            assertThatThrownBy(() -> adapter.register(account))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);

            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("비밀번호가 UTF-8 72바이트를 넘으면 INVALID_INPUT — BCrypt가 뒷부분을 자르는 걸 막는다")
        void rejectsPasswordOverByteLimit() {
            // 한글 1자 = 3바이트. 25자 = 75바이트 > 72
            String tooLong = "가".repeat(25);
            NewAccount overLimit =
                    new NewAccount("gachiga@jnu.ac.kr", tooLong, "후문호랑이", Gender.M, null, null);

            assertThatThrownBy(() -> adapter.register(overLimit))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);

            verify(userRepository, never()).existsByEmail(any());
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("비밀번호가 정확히 72바이트면 통과한다")
        void allowsPasswordAtByteLimit() {
            // 한글 1자 = 3바이트. 24자 = 72바이트
            String atLimit = "가".repeat(24);
            NewAccount exactLimit =
                    new NewAccount("gachiga@jnu.ac.kr", atLimit, "후문호랑이", Gender.M, null, null);
            given(userRepository.existsByEmail("gachiga@jnu.ac.kr")).willReturn(false);
            given(userRepository.existsByNickname("후문호랑이")).willReturn(false);
            given(passwordEncoder.encode(atLimit)).willReturn("{bcrypt}hashed");
            given(userRepository.saveAndFlush(any(User.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            assertThat(adapter.register(exactLimit)).isNotNull();
        }

        @Test
        @DisplayName("사전 검사 통과 뒤 저장 시점 경합(UNIQUE 위반)도 INVALID_INPUT으로 바꾼다")
        void translatesRaceConditionToInvalidInput() {
            given(userRepository.existsByEmail("gachiga@jnu.ac.kr")).willReturn(false);
            given(userRepository.existsByNickname("후문호랑이")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("{bcrypt}hashed");
            given(userRepository.saveAndFlush(any(User.class)))
                    .willThrow(new DataIntegrityViolationException("uk_users_email"));

            assertThatThrownBy(() -> adapter.register(account))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("findAccountById")
    class FindAccountById {

        @Test
        @DisplayName("있으면 AccountProfile로 돌려준다")
        void findsExistingAccount() {
            User user =
                    User.create(
                            "gachiga@jnu.ac.kr", "{bcrypt}hashed", "후문호랑이", Gender.M, "컴퓨터공학과", 3, NOW);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            Optional<AccountProfile> result = adapter.findAccountById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().email()).isEqualTo("gachiga@jnu.ac.kr");
            assertThat(result.get().status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("없으면 빈 값")
        void emptyWhenMissing() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThat(adapter.findAccountById(99L)).isEmpty();
        }

    }

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("이메일·비밀번호가 맞으면 계정을 돌려준다")
        void authenticatesValidCredentials() {
            User user =
                    User.create(
                            "gachiga@jnu.ac.kr", "{bcrypt}hashed", "후문호랑이", Gender.M, null, null, NOW);
            given(userRepository.findByEmail("gachiga@jnu.ac.kr")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password1234", "{bcrypt}hashed")).willReturn(true);

            Optional<AccountProfile> result = adapter.authenticate("gachiga@jnu.ac.kr", "password1234");

            assertThat(result).isPresent();
            assertThat(result.get().email()).isEqualTo("gachiga@jnu.ac.kr");
        }

        @Test
        @DisplayName("없는 이메일이면 빈 값 — 그래도 해시 비교는 한 번 한다(타이밍 공격 방지)")
        void emptyForUnknownEmailButStillHashes() {
            given(userRepository.findByEmail("nobody@jnu.ac.kr")).willReturn(Optional.empty());
            given(passwordEncoder.matches(eq("password1234"), anyString())).willReturn(false);

            Optional<AccountProfile> result = adapter.authenticate("nobody@jnu.ac.kr", "password1234");

            assertThat(result).isEmpty();
            verify(passwordEncoder).matches(eq("password1234"), anyString());
        }

        @Test
        @DisplayName("비밀번호가 틀리면 빈 값")
        void emptyForWrongPassword() {
            User user =
                    User.create(
                            "gachiga@jnu.ac.kr", "{bcrypt}hashed", "후문호랑이", Gender.M, null, null, NOW);
            given(userRepository.findByEmail("gachiga@jnu.ac.kr")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong", "{bcrypt}hashed")).willReturn(false);

            assertThat(adapter.authenticate("gachiga@jnu.ac.kr", "wrong")).isEmpty();
        }

        @Test
        @DisplayName("null·공백 입력은 리포지토리를 부르지 않고 빈 값")
        void emptyForBlankInput() {
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

            assertThat(adapter.authenticate(null, "password1234")).isEmpty();
            assertThat(adapter.authenticate("gachiga@jnu.ac.kr", "")).isEmpty();

            verify(userRepository, never()).findByEmail(any());
        }
    }
}
