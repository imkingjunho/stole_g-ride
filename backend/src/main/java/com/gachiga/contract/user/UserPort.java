package com.gachiga.contract.user;

import java.util.Optional;

/**
 * 사용자 조회. 구현은 {@code user/} (임승현), 사용은 서준(성별 필터)·임승현 {@code realtime/}(닉네임)·
 * 임승현 {@code auth/}(가입 중복 확인)·이승민(이용 제한 확인)이다.
 *
 * <p>구현체는 {@code user/UserAdapter} 하나다 (T1-7 에서 {@code StubUserAdapter} 를 대체).
 */
public interface UserPort {

    /**
     * 사용자 한 명을 찾는다.
     *
     * @param userId 찾을 사용자 id
     * @return 사용자 요약. 없으면 {@link Optional#empty()}. <b>null 을 돌려주지 않는다</b>
     */
    Optional<UserSummary> findById(Long userId);

    /**
     * 이 웹메일 주소로 이미 가입한 계정이 있는지 (FR-01).
     *
     * <p><b>물어본 그 순간의 답일 뿐이다. {@code false} 가 가입 성공을 보장하지 않는다.</b> 인증 코드를
     * 보내고 가입이 끝날 때까지 10분이 벌어지고(PRD §7.1 의 verify:{email} TTL), 그 사이 같은 주소가
     * 먼저 가입될 수 있다. 중복을 실제로 막는 것은 users.email 의 UNIQUE 제약(PRD §7.1)이므로
     * 저장하는 쪽은 그 제약을 반드시 걸고, 위반을 잡아 {@code INVALID_INPUT} 으로 바꿔야 한다.
     * 그러지 않으면 400 이어야 할 응답이 500 으로 나간다.
     *
     * <p><b>비교는 정식 형태로 한다</b> — 앞뒤 공백을 버리고 {@link java.util.Locale#ROOT} 기준 소문자로
     * 바꾼 값. 저장할 때도 같은 형태로 맞춘다. 검사와 저장이 어긋나면 "없다길래 넣었는데 제약에 걸리는"
     * 상태가 생긴다. <b>같은 이메일에서 Redis 키를 만드는 쪽도 같은 형태를 써야 한다</b>(PRD §7.1 의
     * verify:{email}, verify:fail:{email}). 대소문자만 바꿔 재시도하면 인증 실패 횟수가 따로 쌓여
     * 5회 잠금(FR-01)이 무력해진다.
     *
     * <p>이메일은 <b>들어오기만 한다.</b> 이 계약은 이메일을 돌려주지 않는다 — {@link UserSummary} 에
     * 이메일이 없는 것과 같은 이유다(FR-23 최소 수집).
     *
     * <p><b>기본 구현은 임시다.</b> {@code user/UserAdapter} 가 override 하기 전까지 main 빌드를 초록으로
     * 두려는 것이고, 부르면 바로 실패한다. {@code false} 를 돌려주면 중복 가입 검사가 죽은 채로 조용히
     * 지나가기 때문이다. {@code UserAdapter} 가 override 하면 이 기본 구현을 지워 추상 메서드로 되돌린다.
     *
     * @param email 확인할 웹메일 주소. 형식·도메인 검증은 부르는 쪽이 이미 끝낸 상태여야 한다
     * @return 이미 가입했으면 {@code true}. null 이거나 공백뿐이면 <b>예외 없이</b> {@code false}
     */
    default boolean existsByEmail(String email) {
        throw new UnsupportedOperationException(
                "UserPort.existsByEmail 미구현 — user/UserAdapter 에서 override 해야 한다");
    }
}
