package com.gachiga.auth.dto;

/**
 * 웹메일 인증 코드 발송 응답. {@code docs/api-spec.yaml} 의 {@code SignupResponse} 와 1:1 이다.
 *
 * @param expiresInSeconds 인증 코드 유효 시간(초)
 */
public record SignupResponse(String email, int expiresInSeconds) {}
