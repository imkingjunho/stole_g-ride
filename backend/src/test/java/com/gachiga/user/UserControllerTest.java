package com.gachiga.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserStatus;
import com.gachiga.user.dto.UpdateProfileRequest;
import com.gachiga.user.dto.UserProfileResponse;
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
 * {@link UserController} 의 HTTP 계약 검증. {@code docs/api-spec.yaml} 의 {@code UserProfile} 모양을
 * 지키는지 본다. 서비스는 Mock 이다.
 */
@ActiveProfiles("test")
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UserService userService;

    @Test
    @DisplayName("내 프로필을 ApiResponse 로 감싸 돌려준다")
    void meReturnsProfile() throws Exception {
        given(userService.myProfile(1L))
                .willReturn(
                        new UserProfileResponse(
                                1L,
                                "gachiga@jnu.ac.kr",
                                "후문호랑이",
                                Gender.M,
                                "컴퓨터공학과",
                                3,
                                UserStatus.ACTIVE,
                                null));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.nickname").value("후문호랑이"))
                .andExpect(jsonPath("$.data.gender").value("M"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("X-Dev-User 헤더의 사용자가 서비스로 넘어간다")
    void passesCurrentUser() throws Exception {
        given(userService.myProfile(3L))
                .willReturn(
                        new UserProfileResponse(
                                3L,
                                "gachiga3@jnu.ac.kr",
                                "정문부엉이",
                                Gender.M,
                                null,
                                null,
                                UserStatus.ACTIVE,
                                null));

        mockMvc.perform(get("/api/users/me").header("X-Dev-User", "3")).andExpect(status().isOk());

        verify(userService).myProfile(3L);
    }

    @Test
    @DisplayName("가입되지 않은 사용자면 404 NOT_FOUND")
    void returnsNotFoundWhenMissing() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."))
                .given(userService)
                .myProfile(1L);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("프로필 수정에 성공하면 바뀐 프로필을 돌려준다 (T2-5)")
    void updateMeReturnsUpdatedProfile() throws Exception {
        given(userService.updateProfile(eq(1L), any(UpdateProfileRequest.class)))
                .willReturn(
                        new UserProfileResponse(
                                1L,
                                "gachiga@jnu.ac.kr",
                                "용봉동다람쥐",
                                Gender.M,
                                "전자공학과",
                                4,
                                UserStatus.ACTIVE,
                                null));

        mockMvc.perform(
                        patch("/api/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"nickname\":\"용봉동다람쥐\",\"department\":\"전자공학과\",\"grade\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("용봉동다람쥐"))
                .andExpect(jsonPath("$.data.department").value("전자공학과"))
                .andExpect(jsonPath("$.data.grade").value(4));
    }

    @Test
    @DisplayName("닉네임이 2자 미만이면 400 INVALID_INPUT")
    void rejectsTooShortNickname() throws Exception {
        mockMvc.perform(
                        patch("/api/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("중복 닉네임이면 400 INVALID_INPUT")
    void rejectsDuplicateNickname() throws Exception {
        willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "이미 사용 중인 닉네임입니다."))
                .given(userService)
                .updateProfile(eq(1L), any(UpdateProfileRequest.class));

        mockMvc.perform(
                        patch("/api/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"용봉동다람쥐\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
