package com.gachiga.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachiga.auth.dto.SignupResponse;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AuthController} 의 HTTP 계약 검증.
 *
 * <p>{@code docs/api-spec.yaml} 이 약속한 상태 코드와 응답 모양을 지키는지 본다. 서비스는 Mock 이다.
 */
@ActiveProfiles("test")
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private SignupService signupService;

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
}
