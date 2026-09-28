package com.gachiga.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 토큰 재발급 요청. {@code docs/api-spec.yaml} 의 {@code RefreshRequest} 와 1:1이다. */
public record RefreshRequest(
        @NotBlank(message = "refreshToken을 입력해 주세요") String refreshToken) {}
