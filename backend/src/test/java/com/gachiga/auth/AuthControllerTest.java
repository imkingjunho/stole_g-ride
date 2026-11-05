package com.gachiga.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachiga.auth.dto.AuthUserProfile;
import com.gachiga.auth.dto.SignupResponse;
import com.gachiga.auth.dto.TokenResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserStatus;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AuthController} 의 HTTP 계약 검증.
 *
 * <p>{@code docs/api-spec.yaml} 이 약속한 상태 코드와 응답 모양을 지키는지 본다. 서비스는 Mock 이다.
 * {@code logout} 만 {@code @CurrentUser} 를 쓰므로, 그 테스트에서만 {@link SecurityContextHolder} 에
 * 인증을 심는다({@code addFilters = false} 라 진짜 {@code JwtAuthenticationFilter} 가 안 돈다).
 */
@ActiveProfiles("test")
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private SignupService signupService;
    @MockBean private VerifyService verifyService;
    @MockBean private LoginService loginService;
    @MockBean private RefreshService refreshService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("코드 발송에 성공하면 200과 ApiResponse 로 감싼 본문을 돌려준다")
    void signupSucceeds() throws Exception {
        given(signupService.requestCode(any()))
                .willReturn(new SignupResponse("gachiga@jnu.ac.kr", 600));

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"gachiga@jnu.ac.kr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("gachiga@jnu.ac.kr"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("형식이 아닌 이메일은 400 INVALID_INPUT")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("빈 이메일은 400 INVALID_INPUT")
    void rejectsBlankEmail() throws Exception {
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("전남대 웹메일이 아니면 400 EMAIL_DOMAIN_NOT_ALLOWED")
    void rejectsOtherDomain() throws Exception {
        willThrow(new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED))
                .given(signupService)
                .requestCode(any());

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"gachiga@gmail.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DOMAIN_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("잠긴 계정은 429 VERIFY_LOCKED")
    void rejectsLockedAccount() throws Exception {
        willThrow(new BusinessException(ErrorCode.VERIFY_LOCKED))
                .given(signupService)
                .requestCode(any());

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"gachiga@jnu.ac.kr\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("VERIFY_LOCKED"));
    }

    @Test
    @DisplayName("가입 완료에 성공하면 200과 토큰을 돌려준다")
    void verifySucceeds() throws Exception {
        var user =
                new AuthUserProfile(
                        1L, "gachiga@jnu.ac.kr", "후문호랑이", Gender.M, null, null, UserStatus.ACTIVE, null);
        given(verifyService.verify(any()))
                .willReturn(new TokenResponse("access-token", "refresh-token", 1800, user));

        mockMvc.perform(
                        post("/api/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"gachiga@jnu.ac.kr\",\"code\":\"482913\","
                                                + "\"password\":\"password1234\",\"nickname\":\"후문호랑이\","
                                                + "\"gender\":\"M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.email").value("gachiga@jnu.ac.kr"));
    }

    @Test
    @DisplayName("코드 형식이 아니면 400 INVALID_INPUT")
    void rejectsMalformedCode() throws Exception {
        mockMvc.perform(
                        post("/api/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"gachiga@jnu.ac.kr\",\"code\":\"abc\","
                                                + "\"password\":\"password1234\",\"nickname\":\"후문호랑이\","
                                                + "\"gender\":\"M\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("코드가 틀리면 400 VERIFY_CODE_MISMATCH")
    void rejectsMismatchedCode() throws Exception {
        willThrow(new BusinessException(ErrorCode.VERIFY_CODE_MISMATCH))
                .given(verifyService)
                .verify(any());

        mockMvc.perform(
                        post("/api/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"gachiga@jnu.ac.kr\",\"code\":\"000000\","
                                                + "\"password\":\"password1234\",\"nickname\":\"후문호랑이\","
                                                + "\"gender\":\"M\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VERIFY_CODE_MISMATCH"));
    }

    @Test
    @DisplayName("잠긴 계정은 429 VERIFY_LOCKED")
    void rejectsVerifyWhenLocked() throws Exception {
        willThrow(new BusinessException(ErrorCode.VERIFY_LOCKED)).given(verifyService).verify(any());

        mockMvc.perform(
                        post("/api/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"gachiga@jnu.ac.kr\",\"code\":\"482913\","
                                                + "\"password\":\"password1234\",\"nickname\":\"후문호랑이\","
                                                + "\"gender\":\"M\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("VERIFY_LOCKED"));
    }

    @Test
    @DisplayName("로그인에 성공하면 200과 토큰을 돌려준다")
    void loginSucceeds() throws Exception {
        var user =
                new AuthUserProfile(
                        1L, "gachiga@jnu.ac.kr", "후문호랑이", Gender.M, null, null, UserStatus.ACTIVE, null);
        given(loginService.login(any()))
                .willReturn(new TokenResponse("access-token", "refresh-token", 1800, user));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"gachiga@jnu.ac.kr\",\"password\":\"password1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("이메일·비밀번호가 틀리면 401 UNAUTHORIZED")
    void loginRejectsWrongCredentials() throws Exception {
        willThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."))
                .given(loginService)
                .login(any());

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"gachiga@jnu.ac.kr\",\"password\":\"wrong1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("이용 제한 계정은 403 USER_SUSPENDED")
    void loginRejectsSuspendedAccount() throws Exception {
        willThrow(new BusinessException(ErrorCode.USER_SUSPENDED)).given(loginService).login(any());

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"gachiga@jnu.ac.kr\",\"password\":\"password1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_SUSPENDED"));
    }

    @Test
    @DisplayName("재발급에 성공하면 200과 새 토큰을 돌려준다")
    void refreshSucceeds() throws Exception {
        var user =
                new AuthUserProfile(
                        1L, "gachiga@jnu.ac.kr", "후문호랑이", Gender.M, null, null, UserStatus.ACTIVE, null);
        given(refreshService.refresh(any()))
                .willReturn(new TokenResponse("new-access", "new-refresh", 1800, user));

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"some-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("모르는·만료된·이미 회전된 토큰은 401 INVALID_TOKEN")
    void refreshRejectsInvalidToken() throws Exception {
        willThrow(new BusinessException(ErrorCode.INVALID_TOKEN)).given(refreshService).refresh(any());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"stale-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃은 현재 사용자의 토큰을 폐기하고 200을 돌려준다")
    void logoutSucceeds() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(refreshService).logout(1L);
    }
}
