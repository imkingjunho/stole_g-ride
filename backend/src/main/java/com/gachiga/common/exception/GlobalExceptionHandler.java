package com.gachiga.common.exception;

import com.gachiga.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 예외를 {@link ApiResponse} 형태로 바꿔 내보내는 곳 (PRD §14.3).
 *
 * <p>여기 있는 덕분에 컨트롤러·서비스는 try/catch 없이 그냥 예외를 던지면 된다.
 * 처리 대상은 세 가지다.
 *
 * <ol>
 *   <li><b>{@link BusinessException}</b> — 우리가 의도적으로 던진 것. {@link ErrorCode} 의 상태·메시지로 응답
 *   <li><b>검증 실패</b> — {@code @Valid} 는 {@link MethodArgumentNotValidException},
 *       {@code @Validated} 가 붙은 클래스는 {@link ConstraintViolationException} 으로 온다. 둘 다
 *       {@code INVALID_INPUT}
 *   <li><b>그 밖의 모든 예외</b> — 예상 못 한 버그 → {@code INTERNAL_ERROR}. 원인은 로그에만 남긴다
 * </ol>
 *
 * <p>{@link ResponseEntityExceptionHandler} 를 상속하는 이유는 405(메서드 불일치)·415(타입 불일치)·
 * 잘못된 JSON 본문 같은 <b>스프링 MVC 표준 예외까지 전부 {@code ApiResponse} 로 감싸기 위해서</b>다.
 * 상속하지 않으면 그것들이 아래 "그 밖의 모든 예외"로 떨어져 클라이언트 잘못인데도 500 이 나간다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 규칙에 걸린 요청. 버그가 아니므로 평소에는 스택 트레이스 없이 한 줄만 남긴다.
     * 다만 원인 예외를 달고 온 경우(외부 API 실패 등)에는 그 원인까지 남겨야 나중에 추적할 수 있다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException e, WebRequest request) {

        ErrorCode errorCode = e.getErrorCode();
        if (e.getCause() == null) {
            log.warn("비즈니스 예외 [{}] {}", errorCode.name(), e.getMessage());
        } else {
            log.warn("비즈니스 예외 [{}] {}", errorCode.name(), e.getMessage(), e);
        }
        return respond(errorCode, e.getMessage(), request);
    }

    /**
     * 예상하지 못한 오류. 원인은 서버 로그에만 남기고 밖에는 일반 메시지만 내보낸다.
     * 예외 메시지에 내부 구조(쿼리·경로 등)가 드러날 수 있기 때문이다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception e, WebRequest request) {

        log.error("예상하지 못한 예외", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), request);
    }

    /**
     * {@code @Validated} 가 붙은 클래스(컨트롤러·서비스)의 검증 실패.
     *
     * <p>스프링 6.1 은 클래스에 {@code @Validated} 가 있으면 내장 메서드 검증을 끄고 AOP 경로로 넘긴다.
     * 그때 나오는 이 예외는 {@link ResponseEntityExceptionHandler} 가 다루지 않으므로 따로 잡지 않으면
     * 사용자 입력 오류인데도 위의 "그 밖의 모든 예외"로 떨어져 500 이 나간다.
     *
     * <p>참고: 하이버네이트가 엔티티를 저장할 때도 같은 예외를 던진다. 그쪽은 서버 잘못이라 400 이
     * 정확한 답은 아니지만, 엔티티 검증을 쓰기 시작하는 Phase 1 에 다시 본다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException e, WebRequest request) {

        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        StringJoiner details = new StringJoiner(", ");
        if (violations != null) { // 규약상 null 일 수 있다. 여기서 NPE 가 나면 도로 500 이 된다
            violations.forEach(
                    violation ->
                            details.add(
                                    lastNode(violation.getPropertyPath())
                                            + ": "
                                            + violation.getMessage()));
        }

        String message =
                details.length() == 0 ? ErrorCode.INVALID_INPUT.getMessage() : details.toString();
        log.warn("검증 실패 {}", message);
        return respond(ErrorCode.INVALID_INPUT, message, request);
    }

    /**
     * {@code @Valid} 검증 실패. 어느 필드가 왜 틀렸는지 메시지에 담아 프론트가 그대로 보여 줄 수 있게 한다.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        StringJoiner details = new StringJoiner(", ");
        e.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError ->
                        details.add(fieldError.getField() + ": " + fieldError.getDefaultMessage()));
        e.getBindingResult()
                .getGlobalErrors()
                .forEach(globalError -> details.add(globalError.getDefaultMessage()));

        String message =
                details.length() == 0 ? ErrorCode.INVALID_INPUT.getMessage() : details.toString();
        log.warn("검증 실패 {}", message);

        // super 를 거치면 "응답이 이미 나간 뒤인가" 검사를 그대로 물려받는다
        return super.handleExceptionInternal(
                e, ApiResponse.fail(ErrorCode.INVALID_INPUT, message), headers, status, request);
    }

    /**
     * 위에서 따로 다루지 않은 스프링 MVC 표준 예외(405·415·본문 파싱 실패 등)도 같은 껍데기로 감싼다.
     * 상태 코드는 스프링이 정한 것을 그대로 쓰고, 에러 코드만 우리 것으로 바꾼다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ErrorCode errorCode = toErrorCode(status);
        log.warn("MVC 예외 [{}] {}", errorCode.name(), e.getMessage());
        return super.handleExceptionInternal(
                e, ApiResponse.fail(errorCode), headers, status, request);
    }

    /**
     * 실패 응답을 만든다. 응답이 이미 클라이언트로 나가기 시작했다면 본문을 바꿀 수 없으므로 포기한다.
     * 억지로 쓰면 이미 나간 내용 뒤에 JSON 이 덧붙어 파싱할 수 없는 본문이 된다.
     */
    private ResponseEntity<ApiResponse<Void>> respond(
            ErrorCode errorCode, String message, WebRequest request) {

        if (isResponseCommitted(request)) {
            log.warn("응답이 이미 전송되어 본문을 바꾸지 못한다 [{}]", errorCode.name());
            return null;
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode, message));
    }

    /** 응답 헤더가 이미 나갔는지 확인한다 (스트리밍 응답 도중 예외가 난 경우) */
    private boolean isResponseCommitted(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletResponse response = servletWebRequest.getResponse();
            return response != null && response.isCommitted();
        }
        return false;
    }

    /** {@code search.q} 처럼 메서드명이 앞에 붙은 경로에서 마지막 마디(파라미터명)만 남긴다 */
    private String lastNode(Path propertyPath) {
        String name = "";
        for (Path.Node node : propertyPath) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }

    /** HTTP 상태를 가장 가까운 공통 에러 코드로 옮긴다. 4xx 는 요청 잘못, 그 밖은 서버 잘못으로 본다. */
    private ErrorCode toErrorCode(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return ErrorCode.UNAUTHORIZED;
        }
        if (status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return ErrorCode.FORBIDDEN;
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErrorCode.NOT_FOUND;
        }
        return status.is4xxClientError() ? ErrorCode.INVALID_INPUT : ErrorCode.INTERNAL_ERROR;
    }
}
