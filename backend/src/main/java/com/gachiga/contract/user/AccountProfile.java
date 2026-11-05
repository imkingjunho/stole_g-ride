package com.gachiga.contract.user;

import java.time.LocalDateTime;

/**
 * 계정 정보 — 가입·로그인 응답({@code TokenResponse.user}, api-spec {@code UserProfile})을 만들 때 쓴다.
 *
 * <p><b>{@code auth} 만 받는다.</b> 이메일이 들어 있어서다. 다른 모듈에는 이메일을 뺀 {@link UserSummary} 를
 * 넘긴다(FR-23 최소 수집). 이 타입을 {@code auth}·{@code user} 밖에서 쓰면 {@code ArchitectureTest} 가 막는다.
 *
 * <p>필드는 api-spec {@code UserProfile} 과 1:1 이다. 비밀번호 해시는 없다.
 *
 * @param id             사용자 id. null 아님
 * @param email          웹메일 주소(정식 형태). null 아님
 * @param nickname       닉네임. null 아님
 * @param gender         성별. null 아님
 * @param department     학과. null 일 수 있다
 * @param grade          학년. null 일 수 있다
 * @param status         <b>지금 기준</b> 계정 상태 — 저장값이 SUSPENDED 여도 {@code suspendedUntil} 이 지났으면
 *                       ACTIVE 다(FR-04). {@link UserSummary#status()} 와 같은 계산이어야 한다. null 아님
 * @param suspendedUntil 이용 제한이 풀리는 시각. {@code status} 가 SUSPENDED 일 때만 값이 있고, 아니면 null
 */
public record AccountProfile(
        Long id,
        String email,
        String nickname,
        Gender gender,
        String department,
        Integer grade,
        UserStatus status,
        LocalDateTime suspendedUntil) {}
