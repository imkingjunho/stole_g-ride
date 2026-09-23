package com.gachiga.contract.user;

/**
 * 다른 모듈에 넘기는 사용자 정보. <b>여기 있는 것이 전부다.</b>
 *
 * <p>실명·전화번호·이메일은 담지 않는다. 개인정보를 모듈 경계 밖으로 내보내지 않기 위해서다
 * (FR-23 최소 수집, CLAUDE.md §8).
 *
 * @param id       사용자 id. null 아님
 * @param nickname 화면에 보이는 이름. 예: {@code "후문호랑이"}. null 아님
 * @param gender   성별. null 아님
 * @param status   <b>지금 기준</b> 계정 상태 — 저장값이 SUSPENDED 여도 suspendedUntil 이 지났으면 ACTIVE 다(FR-04).
 *                 {@code ride} 가 이 값으로 요청을 막으므로, {@link AccountProfile#status()} 와 같은 계산이어야
 *                 한다. 갈리면 로그인은 되는데 매칭 요청은 영원히 막힌다. null 아님
 */
public record UserSummary(Long id, String nickname, Gender gender, UserStatus status) {}
