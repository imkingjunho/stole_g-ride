package com.gachiga.contract.user;

import java.util.Optional;

/**
 * 사용자 조회. 구현은 {@code user/} (임승현), 사용은 서준(성별 필터)·임승현 {@code realtime/}(닉네임)·
 * 이승민(이용 제한 확인)이다.
 *
 * <p>Phase 0 에는 {@code user/StubUserAdapter} 가 id 1~4 의 고정 사용자를 돌려준다.
 */
public interface UserPort {

    /**
     * 사용자 한 명을 찾는다.
     *
     * @param userId 찾을 사용자 id
     * @return 사용자 요약. 없으면 {@link Optional#empty()}. <b>null 을 돌려주지 않는다</b>
     */
    Optional<UserSummary> findById(Long userId);
}
