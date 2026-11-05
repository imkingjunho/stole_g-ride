package com.gachiga.auth;

import com.gachiga.auth.dto.LoginRequest;
import com.gachiga.auth.dto.RefreshRequest;
import com.gachiga.auth.dto.SignupRequest;
import com.gachiga.auth.dto.SignupResponse;
import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.auth.dto.VerifyRequest;
import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (PRD §8).
 *
 * <p>{@code signup}·{@code verify}·{@code login}·{@code refresh} 는 {@code docs/api-spec.yaml} 이
 * {@code security: []} 로 명시한 대로 인증 없이 열려 있다. {@code logout} 은 반대로 로그인한 사용자만
 * 부를 수 있다 — 어느 계정의 토큰을 지울지 알아야 하기 때문이다.
 */
@Tag(name = "auth", description = "웹메일 인증·JWT (임승현)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;
    private final VerifyService verifyService;
    private final LoginService loginService;
    private final RefreshService refreshService;

    @Operation(
            operationId = "postAuthSignup",
            summary = "[P0] 웹메일 인증 코드 발송",
            description =
                    "`@jnu.ac.kr` 주소로만 가입할 수 있다 (FR-01). 6자리 코드를 메일로 보내고 10분간 "
                            + "유효하다. 도메인이 다르면 EMAIL_DOMAIN_NOT_ALLOWED, 이미 가입된 주소면 "
                            + "INVALID_INPUT, 인증 실패가 쌓여 잠겼으면 VERIFY_LOCKED 가 난다.")
    @SecurityRequirements
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(signupService.requestCode(request));
    }

    @Operation(
            operationId = "postAuthVerify",
            summary = "[P0] 인증 코드 검증 및 가입 완료",
            description =
                    "코드가 맞으면 비밀번호·프로필과 함께 계정을 만들고 토큰을 발급한다 (FR-01·FR-03). "
                            + "5회 틀리면 30분 잠기며 VERIFY_LOCKED 가 난다. "
                            + "성별은 가입 후 변경할 수 없다 — 동성 매칭의 근거이기 때문이다.")
    @SecurityRequirements
    @PostMapping("/verify")
    public ApiResponse<TokenResponse> verify(@Valid @RequestBody VerifyRequest request) {
        return ApiResponse.ok(verifyService.verify(request));
    }

    @Operation(
            operationId = "postAuthLogin",
            summary = "[P0] 로그인",
            description =
                    "Access 30분 / Refresh 14일 (FR-02). 이메일·비밀번호 중 뭐가 틀렸는지는 구분해 주지 "
                            + "않는다(UNAUTHORIZED). 이용 제한 계정은 USER_SUSPENDED 가 난다.")
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(loginService.login(request));
    }

    @Operation(
            operationId = "postAuthRefresh",
            summary = "[P0] 토큰 재발급 (회전)",
            description =
                    "Refresh 토큰을 새 쌍으로 바꾼다. 쓴 토큰은 즉시 폐기된다 (FR-02). 모르는 토큰·이미 "
                            + "회전됐거나 만료된 토큰은 INVALID_TOKEN — 프론트는 이걸 받으면 재로그인시킨다.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(refreshService.refresh(request));
    }

    @Operation(
            operationId = "postAuthLogout",
            summary = "[P1] 로그아웃",
            description = "보유한 Refresh 토큰을 전부 폐기한다. Access 토큰은 만료까지 유효하다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CurrentUser Long userId) {
        refreshService.logout(userId);
        return ApiResponse.ok();
    }
}
