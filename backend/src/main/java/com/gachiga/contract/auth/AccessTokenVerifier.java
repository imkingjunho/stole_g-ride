package com.gachiga.contract.auth;

import java.util.Optional;

/**
 * Access 토큰 검증 계약 (FR-02, PRD §14.1·§14.5).
 *
 * <p>구현은 {@code auth/}(임승현), 사용은 {@code realtime/}(임승현)의 STOMP CONNECT 인증이다.
 * {@code realtime}은 이 계약만 주입받으며 {@code auth}의 JWT 구현 클래스를 직접 참조하지 않는다.
 *
 * <p>토큰의 사용자 식별만 담당한다. 계정 존재·이용 제한 확인과 채팅 그룹 멤버십 인가는 각각의
 * 사용자·그룹 계약으로 별도 처리한다. Access 토큰을 새로 발급하거나 Refresh 토큰을 회전하지 않는다.
 */
public interface AccessTokenVerifier {

    /**
     * 이 서비스가 발급한 Access JWT의 서명·만료·형식과 사용자 ID subject를 검증한다.
     *
     * <p>호출자가 {@code Authorization: Bearer ...} 헤더에서 접두사를 제거한 토큰 원문을 전달한다.
     * 구현체는 서비스의 검증 키로 서명을 검증하고, 만료 시각이 존재하며 아직 지나지 않았는지 확인한다.
     * subject는 유효한 사용자 ID(양수 {@code Long})여야 한다.
     *
     * <p>null·공백·형식 오류·서명 불일치·만료 시각 누락/경과·사용자 ID 오류는 예외 대신
     * {@link Optional#empty()}로 반환한다. Refresh 토큰이나 Bearer 접두사가 붙은 입력도 유효한
     * Access 토큰으로 취급하지 않는다. 호출자는 empty를 받으면 인증을 거부하며 임의 사용자로 대체하지 않는다.
     *
     * <p>토큰 원문·서명 키는 로그 또는 오류 응답에 포함하지 않는다. 검증 설정이나 내부 처리의 장애를
     * 유효한 사용자로 바꾸지 않으며, 호출자도 검증에 실패한 연결을 허용하지 않는다.
     *
     * @param accessToken Bearer 접두사를 제외한 Access 토큰 원문. null·공백은 인증 실패
     * @return 검증된 사용자 ID 또는 empty. null을 반환하지 않는다
     */
    Optional<Long> verify(String accessToken);
}
