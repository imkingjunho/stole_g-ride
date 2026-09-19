package com.gachiga.contract.user;

/**
 * 계정 상태.
 *
 * <p>신고가 쌓이면 {@code SUSPENDED} 가 된다 (E-05). 그 사용자가 매칭 요청을 만들려고 하면
 * {@code ride} 가 {@link UserPort} 로 확인해 {@code USER_SUSPENDED} 로 막는다.
 */
public enum UserStatus {
    /** 정상 이용 가능 */
    ACTIVE,
    /** 신고 누적 등으로 이용 제한 */
    SUSPENDED
}
