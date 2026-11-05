package com.gachiga.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link User} 조회·저장.
 *
 * <p>이메일·닉네임 중복 검사(T1-2·T1-3)와 로그인(T1-4)이 여기 메서드를 쓴다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인, 이메일 중복 가입 검사에 쓴다 */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 가입 시 닉네임 중복 검사(T1-3)에 쓴다 */
    boolean existsByNickname(String nickname);

    /** 프로필 수정 시 닉네임 중복 검사(T2-5). 본인 것은 중복으로 치지 않는다 */
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
