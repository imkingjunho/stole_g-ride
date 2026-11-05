package com.gachiga.contract.user;

import java.util.Optional;

/**
 * 사용자 조회. 구현은 {@code user/} (임승현), 사용은 서준(성별 필터)·임승현 {@code realtime/}(닉네임)·
 * 임승현 {@code auth/}(가입 중복 확인)·이승민(이용 제한 확인)이다.
 *
 * <p>계정을 만들거나 비밀번호를 다루는 일(가입·로그인)은 {@link UserAccountPort} 에 있다. 이 port 는 모든
 * 모듈이 주입받으므로 <b>읽기만</b> 둔다.
 *
 * <p>구현체는 {@code user/UserAdapter} 하나다 (T1-7 에서 {@code StubUserAdapter} 를 대체).
 */
public interface UserPort {

    /**
     * 사용자 한 명을 찾는다.
     *
     * @param userId 찾을 사용자 id
     * @return 사용자 요약. 없으면 {@link Optional#empty()}. <b>null 을 돌려주지 않는다</b>.
     *     {@code status} 는 지금 기준이다 — {@link UserSummary#status()} 참고
     */
    Optional<UserSummary> findById(Long userId);

    /**
     * 이 웹메일 주소로 이미 가입한 계정이 있는지 (FR-01).
     *
     * <p><b>물어본 그 순간의 답일 뿐이다. {@code false} 가 가입 성공을 보장하지 않는다.</b> 인증 코드를
     * 보내고 가입이 끝날 때까지 10분이 벌어지고(PRD §7.1 의 verify:{email} TTL), 그 사이 같은 주소가
     * 먼저 가입될 수 있다. 중복을 실제로 막는 것은 users.email 의 UNIQUE 제약(PRD §7.1)이고, 그 판정은
     * {@link UserAccountPort#register} 가 한다.
     *
     * <p><b>비교는 정식 형태({@link CanonicalEmail#of})로 한다.</b> 저장할 때도 같은 함수로 맞춘다. 같은 이메일에서
     * Redis 키를 만드는 쪽도 같은 함수를 써야 한다(PRD §7.1 의 verify:{email}, verify:fail:{email}). 대소문자만
     * 바꿔 재시도하면 인증 실패 횟수가 따로 쌓여 5회 잠금(FR-01)이 무력해진다.
     *
     * <p>이메일은 <b>들어오기만 한다.</b> 이 계약은 이메일을 돌려주지 않는다 — {@link UserSummary} 에
     * 이메일이 없는 것과 같은 이유다(FR-23 최소 수집).
     *
     * <p><b>이 결과가 {@code POST /api/auth/signup} 의 400 으로 나가면 가입 여부가 드러난다</b>(api-spec 이 그렇게
     * 정했다). 학내 폐쇄 서비스라 받아들이되, {@code /signup} 에는 이메일·IP 기준 횟수 제한이 있어야 한다 —
     * 없으면 학번 패턴으로 가입자 목록을 뽑고 비가입자에게 인증 메일을 쏟아부을 수 있다.
     *
     * @param email 확인할 웹메일 주소. 형식·도메인 검증은 부르는 쪽이 이미 끝낸 상태여야 한다
     * @return 이미 가입했으면 {@code true}. null 이거나 공백뿐이면 <b>예외 없이</b> {@code false}
     */
    boolean existsByEmail(String email);
}
