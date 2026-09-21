package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** {@link ChatMessageRepository} 를 <b>실제 DB(임베디드 H2)</b>로 검증한다. */
@ActiveProfiles("test")
@DataJpaTest
class ChatMessageRepositoryTest {

    @Autowired private ChatMessageRepository repository;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Test
    @DisplayName("최신순으로, 그리고 요청한 개수만큼만 가져온다")
    void findsRecentMessagesNewestFirst() {
        repository.saveAndFlush(
                ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "첫 메시지", NOW));
        repository.saveAndFlush(
                ChatMessage.from(17L, 2L, ChatMessageType.TEXT, "둘째 메시지", NOW.plusMinutes(1)));
        repository.saveAndFlush(
                ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "셋째 메시지", NOW.plusMinutes(2)));

        List<ChatMessage> found =
                repository.findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        17L, NOW.plusMinutes(10), PageRequest.of(0, 2));

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ChatMessage::getContent)
                .containsExactly("셋째 메시지", "둘째 메시지");
    }

    @Test
    @DisplayName("다른 그룹의 메시지는 섞이지 않는다")
    void doesNotMixOtherGroups() {
        repository.saveAndFlush(ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "그룹17", NOW));
        repository.saveAndFlush(ChatMessage.from(99L, 1L, ChatMessageType.TEXT, "그룹99", NOW));

        List<ChatMessage> found =
                repository.findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        17L, NOW.plusMinutes(10), PageRequest.of(0, 10));

        assertThat(found).extracting(ChatMessage::getContent).containsExactly("그룹17");
    }

    @Test
    @DisplayName("cutoff 이후 메시지는 포함하지 않는다")
    void excludesMessagesAfterCutoff() {
        repository.saveAndFlush(ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "이전", NOW));
        repository.saveAndFlush(
                ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "이후", NOW.plusMinutes(5)));

        List<ChatMessage> found =
                repository.findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        17L, NOW.plusMinutes(1), PageRequest.of(0, 10));

        assertThat(found).extracting(ChatMessage::getContent).containsExactly("이전");
    }
}
