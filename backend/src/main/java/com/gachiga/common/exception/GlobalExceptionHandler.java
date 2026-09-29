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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException e, WebRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        if (e.getCause() == null) {
            log.warn("Business exception [{}] {}", errorCode.name(), e.getMessage());
        } else {
            log.warn("Business exception [{}] {}", errorCode.name(), e.getMessage(), e);
        }
        return respond(errorCode, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception e, WebRequest request) {
        log.error("Unexpected exception", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException e, WebRequest request) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        StringJoiner details = new StringJoiner(", ");
        if (violations != null) {
            violations.forEach(
                    violation ->
                            details.add(
                                    lastNode(violation.getPropertyPath())
                                            + ": "
                                            + violation.getMessage()));
        }

        String message =
                details.length() == 0 ? ErrorCode.INVALID_INPUT.getMessage() : details.toString();
        log.warn("Validation failed {}", message);
        return respond(ErrorCode.INVALID_INPUT, message, request);
    }

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
        log.warn("Validation failed {}", message);

        return super.handleExceptionInternal(
                e, ApiResponse.fail(ErrorCode.INVALID_INPUT, message), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ErrorCode errorCode = toErrorCode(status);
        log.warn("MVC exception [{}] {}", errorCode.name(), e.getMessage());
        return super.handleExceptionInternal(
                e, ApiResponse.fail(errorCode), headers, status, request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            ErrorCode errorCode, String message, WebRequest request) {

        if (isResponseCommitted(request)) {
            log.warn("Response already committed [{}]", errorCode.name());
            return null;
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode, message));
    }

    private boolean isResponseCommitted(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletResponse response = servletWebRequest.getResponse();
            return response != null && response.isCommitted();
        }
        return false;
    }

    private String lastNode(Path propertyPath) {
        String name = "";
        for (Path.Node node : propertyPath) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }

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