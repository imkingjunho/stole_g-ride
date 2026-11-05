package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ChatMessageCleanupScheduler} 검증. DB 없이 도는 순수 로직 테스트다 (CLAUDE.md §7). */
@ExtendWith(MockitoExtension.class)
class ChatMessageCleanupSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private static final Clock CLOCK =
            Clock.fixed(
                    NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    private static final ChatProperties PROPERTIES = new ChatProperties(3, 60);

    @Mock private ChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("retentionHours 만큼 전을 cutoff 로 삼아 대상 그룹을 조회한다")
    void queriesWithRetentionCutoff() {
        ChatMessageCleanupScheduler scheduler =
                new ChatMessageCleanupScheduler(chatMessageRepository, PROPERTIES, CLOCK);
        given(chatMessageRepository.findExpiredGroupIds(any())).willReturn(List.of());

        scheduler.deleteExpiredMessages();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chatMessageRepository).findExpiredGroupIds(captor.capture());
        assertThat(captor.getValue()).isEqualTo(NOW.minusHours(3));
    }

    @Test
    @DisplayName("대상 그룹이 없으면 삭제를 호출하지 않는다")
    void doesNothingWhenNoneExpired() {
        ChatMessageCleanupScheduler scheduler =
                new ChatMessageCleanupScheduler(chatMessageRepository, PROPERTIES, CLOCK);
        given(chatMessageRepository.findExpiredGroupIds(any())).willReturn(List.of());

        scheduler.deleteExpiredMessages();

        verify(chatMessageRepository, never()).deleteByGroupId(any());
    }

    @Test
    @DisplayName("대상 그룹마다 deleteByGroupId 를 호출한다")
    void deletesEachExpiredGroup() {
        ChatMessageCleanupScheduler scheduler =
                new ChatMessageCleanupScheduler(chatMessageRepository, PROPERTIES, CLOCK);
        given(chatMessageRepository.findExpiredGroupIds(any())).willReturn(List.of(17L, 99L));
        given(chatMessageRepository.deleteByGroupId(17L)).willReturn(3L);
        given(chatMessageRepository.deleteByGroupId(99L)).willReturn(1L);

        scheduler.deleteExpiredMessages();

        verify(chatMessageRepository).deleteByGroupId(eq(17L));
        verify(chatMessageRepository).deleteByGroupId(eq(99L));
    }
}
