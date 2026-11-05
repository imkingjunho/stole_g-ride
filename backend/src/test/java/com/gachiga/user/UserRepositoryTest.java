package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gachiga.contract.user.Gender;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link UserRepository}를 <b>실제 DB(임베디드 H2)</b>로 검증한다.
 *
 * <p>이메일·닉네임 유니크 제약이 실제로 두 번째 INSERT를 막는지는 Mock으로 확인할 수 없다.
 */
@ActiveProfiles("test")
@DataJpaTest
class UserRepositoryTest {

    @Autowired private UserRepository repository;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 21, 0);

    private User newUser(String email, String nickname) {
        return User.create(email, "{bcrypt}hash", nickname, Gender.M, null, null, NOW);
    }

    @Test
    @DisplayName("이메일로 찾는다")
    void findsByEmail() {
        repository.saveAndFlush(newUser("gachiga@jnu.ac.kr", "후문호랑이"));

        assertThat(repository.findByEmail("gachiga@jnu.ac.kr")).isPresent();
        assertThat(repository.findByEmail("nobody@jnu.ac.kr")).isEmpty();
    }

    @Test
    @DisplayName("이메일 중복 가입은 DB가 막는다")
    void emailMustBeUnique() {
        repository.saveAndFlush(newUser("gachiga@jnu.ac.kr", "후문호랑이"));

        assertThatThrownBy(
                        () -> repository.saveAndFlush(newUser("gachiga@jnu.ac.kr", "용봉동다람쥐")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("닉네임 중복은 DB가 막는다")
    void nicknameMustBeUnique() {
        repository.saveAndFlush(newUser("a@jnu.ac.kr", "후문호랑이"));

        assertThatThrownBy(() -> repository.saveAndFlush(newUser("b@jnu.ac.kr", "후문호랑이")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByEmail·existsByNickname이 저장 여부를 알려준다")
    void existsChecks() {
        repository.saveAndFlush(newUser("gachiga@jnu.ac.kr", "후문호랑이"));

        assertThat(repository.existsByEmail("gachiga@jnu.ac.kr")).isTrue();
        assertThat(repository.existsByEmail("nobody@jnu.ac.kr")).isFalse();
        assertThat(repository.existsByNickname("후문호랑이")).isTrue();
        assertThat(repository.existsByNickname("없는닉네임")).isFalse();
    }

    @Test
    @DisplayName("existsByNicknameAndIdNot — 본인 닉네임은 중복으로 치지 않는다 (T2-5)")
    void existsByNicknameAndIdNotExcludesSelf() {
        User me = repository.saveAndFlush(newUser("me@jnu.ac.kr", "후문호랑이"));
        repository.saveAndFlush(newUser("other@jnu.ac.kr", "용봉동다람쥐"));

        assertThat(repository.existsByNicknameAndIdNot("후문호랑이", me.getId())).isFalse();
        assertThat(repository.existsByNicknameAndIdNot("용봉동다람쥐", me.getId())).isTrue();
    }
}
