package com.gachiga.contract.user;

import java.util.Optional;

/**
 * 계정 수명주기 — 가입, 로그인 검증. 구현은 {@code user/} (임승현), 사용은 임승현 {@code auth/} <b>하나뿐이다.</b>
 * 가입 전 중복 확인은 조회이므로 {@link UserPort#existsByEmail} 에 있다.
 *
 * <p>{@link UserPort} 와 나눈 이유: {@link UserPort} 는 ride·matching·realtime 이 모두 주입받는 <b>조회</b>
 * port 다. 계정 생성과 비밀번호 검증까지 거기 두면 어느 모듈이든 그 일을 할 수 있게 된다. 이 port 와
 * {@link NewAccount}·{@link AccountProfile} 은 {@code auth}·{@code user} 밖에서 쓰면 {@code ArchitectureTest}
 * 가 빌드를 깨뜨린다.
 *
 * <p><b>비밀번호는 이 port 뒤({@code user})에서만 다룬다.</b> 해시를 만드는 것도, 맞춰 보는 것도 {@code user} 가
 * 한다. 그래서 {@code auth} 는 해시를 보지도 만들지도 않고, 인코더가 한 곳에만 있다 — 두 모듈이 각자 인코더를
 * 두면 강도나 알고리즘을 바꿀 때 한쪽만 바뀐다.
 *
 * <p><b>이메일은 정식 형태({@link CanonicalEmail#of})로 다룬다.</b> {@link UserPort#existsByEmail} 과 여기 메서드가
 * 모두 그 함수의 결과로 비교·저장하고, {@code auth} 가 이메일로 만드는 Redis 키(verify:{email},
 * verify:fail:{email})도 같은 함수로 만든다(PRD §7.1). 각자 구현하면 어긋난다.
 *
 * <p><b>{@code status} 는 지금 기준이다.</b> 저장값이 SUSPENDED 여도 {@code suspendedUntil} 이 지났으면 ACTIVE 로
 * 돌려주고 {@code suspendedUntil} 은 null 로 비운다(FR-04 7일 제한). {@link UserPort#findById} 의
 * {@link UserSummary#status()} 도 같은 규칙이다 — 구현({@code user})은 한 메서드로 계산해 두 port 와
 * {@code GET /api/users/me} 가 같은 답을 내게 한다. 갈리면 "로그인은 되는데 매칭 요청은 영원히 403" 이 된다.
 *
 * <p>구현체가 생기기 전에 이 port 를 주입받는 코드를 올리면 컨텍스트가 뜨지 않는다. 구현(user)과 사용(auth)을
 * <b>같은 push</b> 에 올리거나 구현을 먼저 올린다.
 */
public interface UserAccountPort {

    /**
     * 계정을 만든다 (FR-01·FR-03, T1-3). <b>인증 코드 검증은 부르는 쪽({@code auth})이 끝낸 뒤</b>여야 한다.
     *
     * <p>이메일이나 닉네임이 이미 쓰이고 있으면 {@code BusinessException(INVALID_INPUT)} 을 던진다(api-spec
     * {@code /api/auth/verify} 400). 사전 검사를 통과한 뒤 같은 값이 먼저 저장되는 경합도 여기서 같은 예외로 바꿔야
     * 한다 — {@code saveAndFlush} 로 users.email·nickname UNIQUE 위반을 잡아 바꾼다. 그대로 두면 500 이 나간다.
     *
     * <p><b>비밀번호는 UTF-8 72바이트를 넘으면 {@code INVALID_INPUT}.</b> BCrypt 는 72바이트 뒤를 조용히 버려서,
     * 한글 25자 비밀번호는 24자만 맞아도 로그인된다. 명세의 64 "글자" 제한만으로는 못 막는다.
     *
     * @param account 가입 정보. 비밀번호는 평문으로 받고 여기서 해시한다. 이메일은 이미 정식 형태다
     * @return 저장된 계정. 상태는 ACTIVE. null 아님
     */
    AccountProfile register(NewAccount account);

    /**
     * 계정 정보 조회 — 토큰 재발급({@code POST /api/auth/refresh}) 응답의 {@code TokenResponse.user} 를 채울 때
     * 쓴다. 그때는 이메일·비밀번호가 없고 토큰 속 userId 만 있다.
     *
     * @param userId 사용자 id
     * @return 계정. 없으면 빈 값. null 아님
     */
    Optional<AccountProfile> findAccountById(Long userId);

    /**
     * 이메일·비밀번호가 맞는지 확인한다 (FR-02, T1-4).
     *
     * <p>틀린 이유(없는 이메일인지, 비밀번호가 틀렸는지)는 알려 주지 않는다 — 둘 다 빈 값이다. 구분해 주면
     * 가입된 주소를 알아낼 수 있다. 같은 이유로 <b>없는 이메일일 때도 해시 비교를 한 번</b> 해서 응답 시간을 맞춘다.
     * 방법이 정해져 있다: 어댑터를 만들 때 <b>같은 인코더로</b> 더미 해시 하나를 만들어 두고
     * ({@code encoder.encode(UUID.randomUUID().toString())}), 없는 이메일이면 {@code encoder.matches(rawPassword,
     * dummyHash)} 를 부른 뒤 빈 값을 돌려준다. 상수 문자열·null·빈 문자열과 비교하면 안 된다 — BCrypt 는 그 경우
     * 해시 없이 즉시 false 를 주고(0.1ms vs 60ms), {@code DelegatingPasswordEncoder} 는 예외를 던져 500 이 된다.
     * 500 이 나는 것 자체가 "이 이메일은 없다" 를 알려 준다. {@code BusinessException} 외의 예외를 밖으로 내지 않는다.
     *
     * <p>이용 제한 계정도 비밀번호가 맞으면 값을 돌려준다. 403 {@code USER_SUSPENDED} 와 401 을 가르는 것은
     * {@link AccountProfile#status()} 를 보고 부르는 쪽이 한다(api-spec {@code /api/auth/login}).
     *
     * @param email       로그인 이메일. 구현이 {@link CanonicalEmail#of} 로 맞춘다
     * @param rawPassword 평문 비밀번호. 로그에 남기지 않는다
     * @return 맞으면 계정. 없는 이메일·틀린 비밀번호·null·공백이면 빈 값. null 아님
     */
    Optional<AccountProfile> authenticate(String email, String rawPassword);
}
