package com.gachiga.common.response;

import com.gachiga.common.exception.ErrorCode;

/**
 * 모든 REST 응답의 공통 껍데기 (PRD §8·§14.3).
 *
 * <p>성공: {@code {"success": true, "data": {...}, "error": null}}
 * <br>실패: {@code {"success": false, "data": null, "error": {"code": "...", "message": "..."}}}
 *
 * <p>성공이든 실패든 세 필드가 모두 나간다(값이 null 이어도 키는 남는다).
 * 프론트가 {@code error} 키의 존재 여부가 아니라 {@code success} 값으로만 분기하도록 하기 위해서다.
 *
 * <p>컨트롤러는 항상 이 타입을 반환한다. HTTP 상태 코드는
 * {@link com.gachiga.common.exception.GlobalExceptionHandler} 가 {@link ErrorCode} 를 보고 정한다.
 *
 * @param <T>     성공 시 본문 타입. 돌려줄 것이 없으면 {@code Void}
 * @param success 성공 여부
 * @param data    성공 시 본문. 실패 시 null
 * @param error   실패 시 원인. 성공 시 null
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    /** 본문이 있는 성공 응답 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 본문이 없는 성공 응답 (삭제·취소 등) */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 에러 코드의 기본 메시지로 실패 응답을 만든다 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ApiError.of(errorCode));
    }

    /** 상황에 맞는 메시지로 실패 응답을 만든다 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, ApiError.of(errorCode, message));
    }
}
