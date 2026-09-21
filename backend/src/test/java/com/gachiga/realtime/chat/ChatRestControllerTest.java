package com.gachiga.realtime.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.realtime.chat.dto.ChatMessageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** {@link ChatRestController} 의 HTTP 계약 검증. 서비스는 Mock 이다. */
@ActiveProfiles("test")
@WebMvcTest(ChatRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatRestControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ChatService chatService;

    @Test
    @DisplayName("지난 메시지를 오래된 순으로 ApiResponse 로 감싸 돌려준다")
    void returnsMessages() throws Exception {
        given(chatService.recentMessages(eq(17L), eq(1L), isNull(), isNull()))
                .willReturn(
                        List.of(
                                new ChatMessageResponse(
                                        1L,
                                        17L,
                                        3L,
                                        "후문호랑이",
                                        "TEXT",
                                        "안녕하세요",
                                        LocalDateTime.of(2026, 10, 20, 9, 0))));

        mockMvc.perform(get("/api/groups/17/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data[0].senderNickname").value("후문호랑이"));
    }

    @Test
    @DisplayName("그룹 구성원이 아니면 403 GROUP_NOT_MEMBER")
    void rejectsNonMember() throws Exception {
        willThrow(new BusinessException(ErrorCode.GROUP_NOT_MEMBER))
                .given(chatService)
                .recentMessages(eq(17L), eq(1L), any(), any());

        mockMvc.perform(get("/api/groups/17/messages"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_MEMBER"));
    }

    @Test
    @DisplayName("X-Dev-User 헤더의 사용자가 서비스로 넘어간다")
    void passesCurrentUser() throws Exception {
        given(chatService.recentMessages(eq(17L), eq(3L), isNull(), isNull()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/groups/17/messages").header("X-Dev-User", "3"))
                .andExpect(status().isOk());

        verify(chatService).recentMessages(eq(17L), eq(3L), isNull(), isNull());
    }
}
