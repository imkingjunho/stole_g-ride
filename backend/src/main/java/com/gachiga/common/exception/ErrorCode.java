package com.gachiga.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Invalid input"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Email domain not allowed"),
    VERIFY_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "Verify code mismatch"),
    VERIFY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Verify locked"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "User suspended"),
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "Already in queue"),
    REQUEST_TOO_SHORT(HttpStatus.BAD_REQUEST, "Request too short"),
    MATCH_EXPIRED(HttpStatus.GONE, "Match expired"),
    GROUP_NOT_MEMBER(HttpStatus.FORBIDDEN, "Not a group member"),
    ROUTE_API_FAILED(HttpStatus.BAD_GATEWAY, "Route API failed");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}