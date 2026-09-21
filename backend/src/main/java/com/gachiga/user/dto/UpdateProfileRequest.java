package com.gachiga.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 수정 요청. {@code docs/api-spec.yaml} 의 {@code UpdateProfileRequest} 와 1:1 이다.
 *
 * <p>성별은 필드 자체가 없다 — 가입 후 바꿀 수 없다 (FR-03). {@code department}·{@code grade} 는
 * 선택 입력이라 {@code null} 을 보내면 지워진다.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "닉네임을 입력해 주세요")
                @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다")
                String nickname,
        @Size(max = 50, message = "학과 이름이 너무 깁니다") String department,
        @Min(value = 1, message = "학년은 1~6 사이여야 합니다")
                @Max(value = 6, message = "학년은 1~6 사이여야 합니다")
                Integer grade) {}
