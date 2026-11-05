package com.gachiga.user;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.AccountProfile;
import com.gachiga.contract.user.CanonicalEmail;
import com.gachiga.contract.user.NewAccount;
import com.gachiga.contract.user.UserAccountPort;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserAccountPort} 실구현 (T1-3·T1-4). 가입·로그인 검증을 담당한다.
 *
 * <p>비밀번호 해시는 여기서만 만들고 여기서만 맞춰 본다 — {@link UserAccountPort} 계약 참고.
 */
@Component
public class UserAccountAdapter implements UserAccountPort {

    /** BCrypt 가 72바이트 뒤를 조용히 버리는 한계. 넘으면 뒷부분이 무시된 채로 로그인된다 */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 없는 이메일일 때도 소모할 더미 해시. 실제로 이 해시와 맞는 비밀번호는 없다 — {@link
     * #authenticate} 가 이메일 존재 여부와 무관하게 항상 해시 비교를 한 번 하게 만들어 응답 시간으로
     * 계정 존재 여부를 알아낼 수 없게 한다. 생성자에서 실제 인코더로 만들어 둔다 — 상수 문자열을 쓰면
     * 인코더가 바뀌었을 때(예: cost factor) 시간이 어긋날 수 있다.
     */
    private final String dummyHash;

    public UserAccountAdapter(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public AccountProfile register(NewAccount account) {
        // NewAccount 생성자가 이미 CanonicalEmail.of 로 맞춘 값이다.
        requirePasswordWithinByteLimit(account.rawPassword());
        requireEmailAvailable(account.email());
        requireNicknameAvailable(account.nickname());

        User user =
                User.create(
                        account.email(),
                        passwordEncoder.encode(account.rawPassword()),
                        account.nickname(),
                        account.gender(),
                        account.department(),
                        account.grade(),
                        LocalDateTime.now());

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 사전 검사(existsByEmail/existsByNickname) 통과 뒤 경합으로 저장 시점에 걸린 경우.
            // users.email/nickname UNIQUE 위반을 그대로 두면 500이 나간다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 사용 중인 이메일 또는 닉네임입니다.", e);
        }

        return toProfile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountProfile> findAccountById(Long userId) {
        return userRepository.findById(userId).map(this::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountProfile> authenticate(String email, String rawPassword) {
        String canonicalEmail = CanonicalEmail.of(email);
        if (canonicalEmail == null || rawPassword == null || rawPassword.isBlank()) {
            passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
            return Optional.empty();
        }

        Optional<User> found = userRepository.findByEmail(canonicalEmail);
        String hashToCompare = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(rawPassword, hashToCompare);

        if (found.isEmpty() || !matches) {
            return Optional.empty();
        }
        return found.map(this::toProfile);
    }

    private void requirePasswordWithinByteLimit(String rawPassword) {
        int byteLength = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 너무 깁니다.");
        }
    }

    private void requireEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 가입된 이메일입니다.");
        }
    }

    private void requireNicknameAvailable(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 사용 중인 닉네임입니다.");
        }
    }

    private AccountProfile toProfile(User user) {
        LocalDateTime now = LocalDateTime.now();
        return new AccountProfile(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getGender(),
                user.getDepartment(),
                user.getGrade(),
                user.currentStatus(now),
                user.currentSuspendedUntil(now));
    }
}
