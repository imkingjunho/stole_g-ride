package com.gachiga.common.response;

import com.gachiga.common.exception.ErrorCode;

/**
 * 실패 응답의 본문. {@link ApiResponse#error()} 에 담긴다.
 *
 * <p>프론트는 {@code code} 로 분기하고 {@code message} 를 사용자에게 보여 준다.
 * 따라서 {@code code} 는 절대 바뀌면 안 되는 계약이고(= {@link ErrorCode} 의 이름),
 * {@code message} 는 언제든 다듬어도 되는 안내 문구다.
 *
 * @param code    에러 코드 이름. 예: {@code "ALREADY_IN_QUEUE"}. null 아님
 * @param message 사용자에게 보여 줄 한국어 설명. null 아님
 */
public record ApiError(String code, String message) {

    /** {@link ErrorCode} 의 기본 메시지를 그대로 쓴다. */
    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.name(), errorCode.getMessage());
    }

    /** 기본 메시지 대신 상황에 맞는 설명을 넣는다. 예: 검증 실패한 필드 이름 */
    public static ApiError of(ErrorCode errorCode, String message) {
        return new ApiError(errorCode.name(), message);
    }
}
