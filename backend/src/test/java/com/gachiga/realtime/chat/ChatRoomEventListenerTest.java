package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

/** {@link ChatRoomEventListener} 검증. */
@ExtendWith(MockitoExtension.class)
class ChatRoomEventListenerTest {

    @Mock private ChatService chatService;
    @Mock private ChatMessageRepository chatMessageRepository;

    private ChatRoomEventListener listener;

    @Nested
    @DisplayName("GroupConfirmed")
    class Confirmed {

        @Test
        @DisplayName("안내 메시지를 남긴다")
        void sendsSystemMessage() {
            listener = new ChatRoomEventListener(chatService);

            listener.on(new GroupConfirmed(17L, List.of(1L, 2L)));

            verify(chatService)
                    .systemMessage(eq(17L), eq("매칭이 성사됐어요. 만남 안내를 확인하고 정확한 위치를 이야기해 보세요."));
        }

        @Test
        @DisplayName("ChatService 가 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
        void doesNotPropagateFailure() {
            listener = new ChatRoomEventListener(chatService);
            willThrow(new RuntimeException("DB 오류"))
                    .given(chatService)
                    .systemMessage(eq(17L), anyString());

            assertThatCode(() -> listener.on(new GroupConfirmed(17L, List.of(1L))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("GroupCompleted")
    class Completed {

        @Test
        @DisplayName("완료 안내만 보내고 대화는 보존한다")
        void keepsMessagesAfterCompletion() {
            listener = new ChatRoomEventListener(chatService);

            listener.on(new GroupCompleted(17L, List.of(1L, 2L)));

            verify(chatService).systemMessage(eq(17L), anyString());
            org.mockito.Mockito.verifyNoInteractions(chatMessageRepository);
        }

        @Test
        @DisplayName("삭제가 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
        void doesNotPropagateFailure() {
            listener = new ChatRoomEventListener(chatService);
            willThrow(new RuntimeException("DB 오류"))
                    .given(chatService)
                    .systemMessage(eq(17L), anyString());

            assertThatCode(() -> listener.on(new GroupCompleted(17L, List.of(1L))))
                    .doesNotThrowAnyException();
        }
    }
}
