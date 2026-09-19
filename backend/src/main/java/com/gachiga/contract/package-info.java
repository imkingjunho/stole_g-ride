/**
 * <b>동결 계약(frozen contract).</b> 모듈 사이에 오가는 것은 여기 있는 것이 전부다 (PRD §14).
 *
 * <p>규칙은 세 가지다.
 *
 * <ol>
 *   <li>이 패키지는 <b>읽기 전용</b>이다. 수정은 이승민이 {@code [계약 변경 요청]} 을 받아서만 한다 (PRD §14.8)
 *   <li>다른 모듈의 기능이 필요하면 여기 port 인터페이스를 주입받아 쓴다.
 *       <b>자기 패키지에 비슷한 인터페이스를 새로 만들어 우회하지 않는다</b>
 *   <li>이 패키지는 어떤 도메인 패키지({@code ride}, {@code matching}, …)도 import 하지 않는다.
 *       방향은 언제나 도메인 → 계약이다
 * </ol>
 *
 * <p>구성:
 *
 * <ul>
 *   <li>{@code route} — 경로·요금·거점 (구현: 송준호)
 *   <li>{@code user} — 사용자 요약 (구현: 임승현)
 *   <li>{@code ride} — 대기 요청·대기 상태 (구현: 이승민)
 *   <li>{@code matching} — 그룹 구성원 조회 (구현: 서준)
 *   <li>{@code auth} — {@code @CurrentUser} (리졸버 구현: 임승현)
 *   <li>{@code event} — 모듈 간 알림용 이벤트 8종
 * </ul>
 */
package com.gachiga.contract;
