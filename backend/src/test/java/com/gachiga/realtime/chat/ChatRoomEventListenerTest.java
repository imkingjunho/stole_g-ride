package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
            listener = new ChatRoomEventListener(chatService, chatMessageRepository);

            listener.on(new GroupConfirmed(17L, List.of(1L, 2L)));

            verify(chatService)
                    .systemMessage(eq(17L), eq("매칭이 성사됐어요. 만날 장소를 정해 보세요"));
        }

        @Test
        @DisplayName("ChatService 가 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
        void doesNotPropagateFailure() {
            listener = new ChatRoomEventListener(chatService, chatMessageRepository);
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
        @DisplayName("메시지를 즉시 지운다 (T1-13)")
        void deletesMessagesImmediately() {
            listener = new ChatRoomEventListener(chatService, chatMessageRepository);
            given(chatMessageRepository.deleteByGroupId(17L)).willReturn(5L);

            listener.on(new GroupCompleted(17L, List.of(1L, 2L)));

            verify(chatMessageRepository).deleteByGroupId(17L);
        }

        @Test
        @DisplayName("삭제가 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
        void doesNotPropagateFailure() {
            listener = new ChatRoomEventListener(chatService, chatMessageRepository);
            willThrow(new RuntimeException("DB 오류"))
                    .given(chatMessageRepository)
                    .deleteByGroupId(17L);

            assertThatCode(() -> listener.on(new GroupCompleted(17L, List.of(1L))))
                    .doesNotThrowAnyException();
        }
    }
}
