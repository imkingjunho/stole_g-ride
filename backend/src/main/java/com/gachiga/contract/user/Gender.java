package com.gachiga.contract.user;

/**
 * 성별. 동성 옵션 필터에 쓴다 (PRD §5.1 필터 3, E-06).
 *
 * <p>가입 후 변경할 수 없다 — 동성 매칭의 근거이기 때문이다 (FR-04).
 */
public enum Gender {
    /** 남성 */
    M,
    /** 여성 */
    F
}
