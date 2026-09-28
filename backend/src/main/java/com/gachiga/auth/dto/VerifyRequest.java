package com.gachiga.auth.dto;

import com.gachiga.contract.user.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 인증 코드 검증·가입 완료 요청. {@code docs/api-spec.yaml} 의 {@code VerifyRequest} 와 1:1이다.
 *
 * <p>실명·전화번호 필드는 없다 (CLAUDE.md §8). {@code department}·{@code grade} 는 선택 입력이다.
 */
public record VerifyRequest(
        @NotBlank(message = "이메일을 입력해 주세요")
                @Email(message = "이메일 형식이 올바르지 않습니다")
                String email,
        @NotBlank(message = "인증 코드를 입력해 주세요")
                @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드는 숫자 6자리입니다")
                String code,
        @NotBlank(message = "비밀번호를 입력해 주세요")
                @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다")
                String password,
        @NotBlank(message = "닉네임을 입력해 주세요")
                @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다")
                String nickname,
        @NotNull(message = "성별을 선택해 주세요") Gender gender,
        @Size(max = 50, message = "학과 이름이 너무 깁니다") String department,
        @Min(value = 1, message = "학년은 1~6 사이여야 합니다")
                @Max(value = 6, message = "학년은 1~6 사이여야 합니다")
                Integer grade) {}
