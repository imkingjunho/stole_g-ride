package com.gachiga.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서비스 전체의 에러 코드 목록 (PRD §8).
 *
 * <p>이름(={@code code})은 프론트와의 계약이므로 바꾸지 않는다. 메시지는 다듬어도 된다.
 * <b>새 코드가 필요하면 직접 추가하지 말고 단톡방에 {@code [에러코드 요청]} 을 보낸다</b>(PRD §13.7).
 * 이 파일은 이승민 소유다.
 *
 * <p>각 코드는 HTTP 상태를 함께 가진다. 컨트롤러·서비스는 상태 코드를 신경 쓰지 말고
 * {@code throw new BusinessException(ErrorCode.XXX)} 만 하면 된다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ────────────────────────────────────────────────
    /** 요청 값이 형식·범위에 맞지 않음. {@code @Valid} 검증 실패도 여기로 모인다 */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    /** 로그인이 필요하거나 토큰이 없음 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    /** 로그인은 했지만 권한이 없음 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    /** 대상 리소스가 없음 */
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    /** 예상하지 못한 서버 오류. 원인은 서버 로그에만 남긴다 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // ── 인증·계정 (auth, user — 임승현) ─────────────────────
    /** 전남대 웹메일이 아닌 주소로 가입 시도 (FR-01) */
    EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "전남대 웹메일 주소로만 가입할 수 있습니다."),
    /** 인증 코드가 틀렸거나 만료됨 */
    VERIFY_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    /** 인증 코드를 여러 번 틀려 잠김 */
    VERIFY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    /** JWT 가 위조·만료됨 (프론트는 이 코드를 보고 refresh 를 시도한다 — E-09) */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다. 다시 로그인해 주세요."),
    /** 신고 누적으로 이용이 제한된 사용자 (E-05) */
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),

    // ── 매칭 요청 (ride — 이승민) ───────────────────────────
    /** 진행 중인 요청이 이미 있음. 1인 1요청 (FR-08) */
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "이미 진행 중인 매칭 요청이 있습니다."),
    /** 거점에서 목적지까지가 너무 가까움. 최소 500m (E-08) */
    REQUEST_TOO_SHORT(HttpStatus.BAD_REQUEST, "출발 거점에서 500m 이상 떨어진 목적지를 선택해 주세요."),

    // ── 매칭·그룹 (matching — 서준) ─────────────────────────
    /** 대기 시간이 지나 요청이 만료됨 (FR-10) */
    MATCH_EXPIRED(HttpStatus.GONE, "대기 시간이 만료되었습니다. 다시 요청해 주세요."),
    /** 그룹 구성원이 아닌 사용자의 접근 (채팅 구독·그룹 조회) */
    GROUP_NOT_MEMBER(HttpStatus.FORBIDDEN, "해당 매칭 그룹의 구성원이 아닙니다."),

    // ── 경로 (route — 송준호) ───────────────────────────────
    /**
     * 카카오 길찾기 호출 실패. 보통은 Haversine 추정치로 대체하므로(E-03) 이 코드까지 오는 일은 드물다.
     * 추정치조차 만들 수 없을 때만 쓴다.
     */
    ROUTE_API_FAILED(HttpStatus.BAD_GATEWAY, "경로를 계산하지 못했습니다. 잠시 후 다시 시도해 주세요.");

    /** 이 코드로 응답할 때의 HTTP 상태 */
    private final HttpStatus status;

    /** 사용자에게 보여 줄 기본 메시지. 상황에 따라 덮어쓸 수 있다 */
    private final String message;
}
