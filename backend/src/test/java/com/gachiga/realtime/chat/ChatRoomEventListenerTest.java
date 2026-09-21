package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.GroupConfirmed;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ChatRoomEventListener} 검증. */
@ExtendWith(MockitoExtension.class)
class ChatRoomEventListenerTest {

    @Mock private ChatService chatService;

    private ChatRoomEventListener listener;

    @Test
    @DisplayName("GroupConfirmed 를 받으면 안내 메시지를 남긴다")
    void sendsSystemMessageOnConfirmed() {
        listener = new ChatRoomEventListener(chatService);

        listener.on(new GroupConfirmed(17L, List.of(1L, 2L)));

        verify(chatService).systemMessage(eq(17L), eq("매칭이 성사됐어요. 만날 장소를 정해 보세요"));
    }

    @Test
    @DisplayName("ChatService 가 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
    void doesNotPropagateFailure() {
        listener = new ChatRoomEventListener(chatService);
        willThrow(new RuntimeException("DB 오류"))
                .given(chatService)
                .systemMessage(eq(17L), org.mockito.ArgumentMatchers.anyString());

        assertThatCode(() -> listener.on(new GroupConfirmed(17L, List.of(1L))))
                .doesNotThrowAnyException();
    }
}
