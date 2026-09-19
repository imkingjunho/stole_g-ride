package com.gachiga.user;

import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserPort;
import com.gachiga.contract.user.UserStatus;
import com.gachiga.contract.user.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * ⚠️ <b>Phase 0 스텁</b> — 고정된 사용자 네 명을 돌려준다 (PRD §14.7).
 *
 * <p>{@code auth/DevCurrentUserResolver} 가 {@code X-Dev-User} 헤더로 고르는 id 와 같은 1~4 다.
 * 성별을 섞어 두었으므로 동성 옵션 필터(E-06)를 눈으로 확인할 수 있다.
 *
 * <p><b>교체 담당: 임승현 · Phase 1.</b> {@code users} 테이블을 읽는 구현으로 바꾸고 이 클래스를 삭제한다.
 */
@Component
public class StubUserAdapter implements UserPort {

    private static final Map<Long, UserSummary> USERS =
            List.of(
                            new UserSummary(1L, "후문호랑이", Gender.M, UserStatus.ACTIVE),
                            new UserSummary(2L, "용봉동다람쥐", Gender.F, UserStatus.ACTIVE),
                            new UserSummary(3L, "정문부엉이", Gender.M, UserStatus.ACTIVE),
                            new UserSummary(4L, "송정역고양이", Gender.F, UserStatus.ACTIVE))
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(UserSummary::id, Function.identity()));

    @Override
    public Optional<UserSummary> findById(Long userId) {
        return Optional.ofNullable(USERS.get(userId));
    }
}
