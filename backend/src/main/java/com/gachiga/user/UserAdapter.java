package com.gachiga.user;

import com.gachiga.contract.user.CanonicalEmail;
import com.gachiga.contract.user.UserPort;
import com.gachiga.contract.user.UserSummary;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link UserPort} 실구현 (T1-7). {@code users} 테이블을 읽는다.
 *
 * <p>{@code StubUserAdapter}(id 1~4 고정)를 대체한다.
 */
@Component
@RequiredArgsConstructor
public class UserAdapter implements UserPort {

    private final UserRepository userRepository;

    @Override
    public Optional<UserSummary> findById(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository
                .findById(userId)
                .map(
                        user ->
                                new UserSummary(
                                        user.getId(),
                                        user.getNickname(),
                                        user.getGender(),
                                        user.currentStatus(now)));
    }

    @Override
    public boolean existsByEmail(String email) {
        String canonical = CanonicalEmail.of(email);
        if (canonical == null) {
            return false;
        }
        return userRepository.existsByEmail(canonical);
    }
}
