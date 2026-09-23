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
 * <p><b>이메일은 정식 형태로 다룬다</b> — 앞뒤 공백을 버리고 {@link java.util.Locale#ROOT} 소문자.
 * {@link UserPort#existsByEmail} 과 여기 두 메서드가 모두 같은 규칙으로 비교·저장한다. {@code auth} 가 이메일로
 * 만드는 Redis 키(verify:{email}, verify:fail:{email})도 같은 형태여야 한다(PRD §7.1).
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
     * 한다 — users.email·nickname UNIQUE 위반을 그대로 두면 500 이 나간다.
     *
     * @param account 가입 정보. 비밀번호는 평문으로 받고 여기서 해시한다
     * @return 저장된 계정. 이메일은 정식 형태, 상태는 ACTIVE. null 아님
     */
    AccountProfile register(NewAccount account);

    /**
     * 이메일·비밀번호가 맞는지 확인한다 (FR-02, T1-4).
     *
     * <p>틀린 이유(없는 이메일인지, 비밀번호가 틀렸는지)는 알려 주지 않는다 — 둘 다 빈 값이다. 구분해 주면
     * 가입된 주소를 알아낼 수 있다. 같은 이유로 <b>없는 이메일일 때도 해시 비교를 한 번 해서</b> 응답 시간을 맞춘다.
     *
     * <p>이용 제한 계정도 비밀번호가 맞으면 값을 돌려준다. 403 {@code USER_SUSPENDED} 와 401 을 가르는 것은
     * {@link AccountProfile#status()} 를 보고 부르는 쪽이 한다(api-spec {@code /api/auth/login}).
     *
     * @param email       로그인 이메일
     * @param rawPassword 평문 비밀번호. 로그에 남기지 않는다
     * @return 맞으면 계정. 없는 이메일·틀린 비밀번호·null·공백이면 빈 값. null 아님
     */
    Optional<AccountProfile> authenticate(String email, String rawPassword);
}
