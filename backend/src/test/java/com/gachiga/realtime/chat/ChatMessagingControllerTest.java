package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.realtime.StompPrincipal;
import com.gachiga.realtime.chat.dto.ChatSendRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ChatMessagingController} 검증 — Principal 을 사용자 id 로 바꿔 {@link ChatService} 에 넘기는지 */
@ExtendWith(MockitoExtension.class)
class ChatMessagingControllerTest {

    @Mock private ChatService chatService;

    @Test
    @DisplayName("Principal 의 사용자 id 로 ChatService.send 를 호출한다")
    void delegatesToChatService() {
        ChatMessagingController controller = new ChatMessagingController(chatService);
        ChatSendRequest request = new ChatSendRequest("TEXT", "안녕하세요");

        controller.send(17L, new StompPrincipal("3"), request);

        verify(chatService).send(eq(17L), eq(3L), eq(request));
    }

    @Test
    @DisplayName("Principal 이 없으면 UNAUTHORIZED")
    void rejectsWithoutPrincipal() {
        ChatMessagingController controller = new ChatMessagingController(chatService);

        assertThatThrownBy(() -> controller.send(17L, null, new ChatSendRequest("TEXT", "안녕")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
