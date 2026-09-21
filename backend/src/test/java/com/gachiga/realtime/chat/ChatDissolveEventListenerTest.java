package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.GroupDissolved;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

/** {@link ChatDissolveEventListener} 검증 (T2-4, E-10). */
@ExtendWith(MockitoExtension.class)
class ChatDissolveEventListenerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);
    private static final Clock CLOCK =
            Clock.fixed(
                    NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    @Mock private ChatService chatService;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private TaskScheduler taskScheduler;

    private ChatDissolveEventListener listener;

    @Nested
    @DisplayName("안내 메시지")
    class SystemMessage {

        @Test
        @DisplayName("동승자 이탈이면 그에 맞는 문구를 남긴다")
        void memberLeftMessage() {
            listener =
                    new ChatDissolveEventListener(
                            chatService, chatMessageRepository, taskScheduler, CLOCK);

            listener.on(new GroupDissolved(17L, List.of(1L, 2L), "MEMBER_LEFT"));

            verify(chatService).systemMessage(eq(17L), contains("동승자"));
        }

        @Test
        @DisplayName("모르는 사유는 일반 문구를 남긴다")
        void unknownReasonMessage() {
            listener =
                    new ChatDissolveEventListener(
                            chatService, chatMessageRepository, taskScheduler, CLOCK);

            listener.on(new GroupDissolved(17L, List.of(1L), "SOMETHING_ELSE"));

            verify(chatService).systemMessage(eq(17L), contains("그룹이 해체됐어요"));
        }
    }

    @Nested
    @DisplayName("삭제 예약")
    class ScheduleDeletion {

        @Test
        @DisplayName("5분 뒤 시각으로 예약한다")
        void schedulesFiveMinutesLater() {
            listener =
                    new ChatDissolveEventListener(
                            chatService, chatMessageRepository, taskScheduler, CLOCK);

            listener.on(new GroupDissolved(17L, List.of(1L), "MEMBER_LEFT"));

            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
            assertThat(captor.getValue()).isEqualTo(CLOCK.instant().plusSeconds(300));
        }

        @Test
        @DisplayName("예약된 작업이 실행되면 메시지를 지운다")
        void deletesWhenScheduledTaskRuns() {
            listener =
                    new ChatDissolveEventListener(
                            chatService, chatMessageRepository, taskScheduler, CLOCK);
            given(chatMessageRepository.deleteByGroupId(17L)).willReturn(4L);

            listener.on(new GroupDissolved(17L, List.of(1L), "MEMBER_LEFT"));

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(taskScheduler).schedule(captor.capture(), any(Instant.class));
            captor.getValue().run();

            verify(chatMessageRepository).deleteByGroupId(17L);
        }

        @Test
        @DisplayName("예약된 작업이 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
        void scheduledTaskDoesNotPropagateFailure() {
            listener =
                    new ChatDissolveEventListener(
                            chatService, chatMessageRepository, taskScheduler, CLOCK);
            willThrow(new RuntimeException("DB 오류"))
                    .given(chatMessageRepository)
                    .deleteByGroupId(17L);

            listener.on(new GroupDissolved(17L, List.of(1L), "MEMBER_LEFT"));

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(taskScheduler).schedule(captor.capture(), any(Instant.class));

            assertThatCode(() -> captor.getValue().run()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("ChatService 가 실패하면 삭제도 예약하지 않고, 예외는 밖으로 내보내지 않는다 (CLAUDE.md §3)")
    void doesNotScheduleOrPropagateOnFailure() {
        listener =
                new ChatDissolveEventListener(
                        chatService, chatMessageRepository, taskScheduler, CLOCK);
        willThrow(new RuntimeException("DB 오류"))
                .given(chatService)
                .systemMessage(eq(17L), anyString());

        assertThatCode(() -> listener.on(new GroupDissolved(17L, List.of(1L), "MEMBER_LEFT")))
                .doesNotThrowAnyException();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
