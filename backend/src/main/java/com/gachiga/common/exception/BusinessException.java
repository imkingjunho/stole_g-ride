package com.gachiga.common.exception;

import lombok.Getter;

/**
 * 우리가 의도적으로 던지는 예외. "이건 버그가 아니라 규칙에 걸린 것"을 뜻한다.
 *
 * <p>{@link GlobalExceptionHandler} 가 이 예외를 잡아 {@link ErrorCode} 의 HTTP 상태와 메시지로 응답한다.
 * 그러므로 서비스 코드는 상태 코드나 응답 형태를 신경 쓸 필요가 없다.
 *
 * <pre>{@code
 * if (rideRequestRepository.existsInProgress(userId)) {
 *     throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
 * }
 * }</pre>
 *
 * <p><b>{@code RuntimeException} 을 직접 던지지 않는다</b>(CLAUDE.md §5). 그렇게 하면
 * 예상 못 한 오류로 취급되어 사용자에게 500 이 나간다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 에러 코드의 기본 메시지를 그대로 쓴다 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황을 구체적으로 설명한다. 이 문구는 사용자에게 그대로 보인다 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 원인 예외를 함께 남긴다. 사용자에게는 {@code message} 만 보이고 원인은 로그에만 남는다 */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
