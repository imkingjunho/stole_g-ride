package com.gachiga.auth;

import com.gachiga.auth.dto.SignupRequest;
import com.gachiga.auth.dto.SignupResponse;
import com.gachiga.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (PRD §8). 로그인 전에도 부를 수 있는 엔드포인트만 담는다.
 *
 * <p>{@code docs/api-spec.yaml} 이 {@code security: []} 로 명시한다 — 이 컨트롤러는 인증 없이 열려
 * 있어야 한다.
 */
@Tag(name = "auth", description = "웹메일 인증·JWT (임승현)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;

    @Operation(
            operationId = "postAuthSignup",
            summary = "[P0] 웹메일 인증 코드 발송",
            description =
                    "`@jnu.ac.kr` 주소로만 가입할 수 있다 (FR-01). 6자리 코드를 메일로 보내고 10분간 "
                            + "유효하다. 도메인이 다르면 EMAIL_DOMAIN_NOT_ALLOWED, 인증 실패가 쌓여 "
                            + "잠겼으면 VERIFY_LOCKED 가 난다. "
                            + "(TODO: 이미 가입된 주소 거절은 UserPort.existsByEmail 계약 확정 뒤 추가)")
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(signupService.requestCode(request));
    }
}
