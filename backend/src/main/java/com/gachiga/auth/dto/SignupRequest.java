package com.gachiga.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 웹메일 인증 코드 발송 요청. {@code docs/api-spec.yaml} 의 {@code SignupRequest} 와 1:1 이다.
 *
 * <p>도메인이 {@code @jnu.ac.kr} 인지는 형식 검증이 아니라 서비스에서 본다 — 틀렸을 때 돌려줄
 * 에러 코드({@code EMAIL_DOMAIN_NOT_ALLOWED})가 일반 형식 오류와 다르기 때문이다.
 */
public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요")
                @Email(message = "이메일 형식이 올바르지 않습니다")
                String email) {}
