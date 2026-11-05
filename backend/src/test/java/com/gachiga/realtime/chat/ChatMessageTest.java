package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link ChatMessage} 생성값 검증. DB 없이 도는 순수 로직 테스트다 (CLAUDE.md §7). */
class ChatMessageTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Nested
    @DisplayName("사용자 메시지")
    class From {

        @Test
        @DisplayName("TEXT·QUICK 타입으로 만들 수 있다")
        void createsTextAndQuick() {
            ChatMessage text = ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "안녕하세요", NOW);
            ChatMessage quick = ChatMessage.from(17L, 1L, ChatMessageType.QUICK, "도착했어요", NOW);

            assertThat(text.getType()).isEqualTo(ChatMessageType.TEXT);
            assertThat(quick.getType()).isEqualTo(ChatMessageType.QUICK);
        }

        @Test
        @DisplayName("입력값을 그대로 보관한다")
        void keepsGivenValues() {
            ChatMessage message = ChatMessage.from(17L, 3L, ChatMessageType.TEXT, "안녕하세요", NOW);

            assertThat(message.getGroupId()).isEqualTo(17L);
            assertThat(message.getSenderId()).isEqualTo(3L);
            assertThat(message.getContent()).isEqualTo("안녕하세요");
            assertThat(message.getCreatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("SYSTEM 타입으로는 만들 수 없다 — system() 을 써야 한다")
        void rejectsSystemType() {
            assertThatThrownBy(
                            () ->
                                    ChatMessage.from(
                                            17L, 1L, ChatMessageType.SYSTEM, "안내", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("system() 은 발신자가 0번 고정이다")
    void systemMessageHasFixedSender() {
        ChatMessage message = ChatMessage.system(17L, "매칭이 성사됐어요", NOW);

        assertThat(message.getSenderId()).isEqualTo(ChatMessage.SYSTEM_SENDER_ID);
        assertThat(message.getType()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(message.getContent()).isEqualTo("매칭이 성사됐어요");
    }
}
