package com.gachiga.auth.dto;

import jakarta.validation.constraints.*;

/** 재설정 코드는 가입 코드와 별도로 취급한다. */
public record PasswordResetRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
        @NotBlank @Size(min = 8, max = 64) String password) {
    @Override
    public String toString() {
        return "PasswordResetRequest[인증 정보 숨김]";
    }
}
