package com.gachiga.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.user.dto.ReportRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** {@link ReportController} 의 HTTP 계약 검증. 서비스는 Mock 이다. */
@ActiveProfiles("test")
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    private static final String BODY =
            "{\"groupId\":17,\"reportedUserId\":2,\"reason\":\"NO_SHOW\",\"detail\":\"안 왔어요\"}";

    @Autowired private MockMvc mockMvc;

    @MockBean private ReportService reportService;

    @Test
    @DisplayName("신고에 성공하면 200과 빈 본문을 돌려준다")
    void reportSucceeds() throws Exception {
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reportService).report(eq(1L), any(ReportRequest.class));
    }

    @Test
    @DisplayName("X-Dev-User 헤더의 사용자가 신고자로 넘어간다")
    void passesCurrentUserAsReporter() throws Exception {
        mockMvc.perform(
                        post("/api/reports")
                                .header("X-Dev-User", "3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk());

        verify(reportService).report(eq(3L), any(ReportRequest.class));
    }

    @Test
    @DisplayName("허용되지 않은 사유 값은 400 INVALID_INPUT")
    void rejectsUnknownReason() throws Exception {
        mockMvc.perform(
                        post("/api/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"groupId\":17,\"reportedUserId\":2,\"reason\":\"MADE_UP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("그룹 구성원이 아니면 403 GROUP_NOT_MEMBER")
    void rejectsNonMember() throws Exception {
        willThrow(new BusinessException(ErrorCode.GROUP_NOT_MEMBER))
                .given(reportService)
                .report(eq(1L), any(ReportRequest.class));

        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_MEMBER"));
    }
}
