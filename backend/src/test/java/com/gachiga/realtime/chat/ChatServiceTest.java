package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.matching.MatchHistoryPort;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserPort;
import com.gachiga.contract.user.UserStatus;
import com.gachiga.contract.user.UserSummary;
import com.gachiga.realtime.chat.dto.ChatMessageResponse;
import com.gachiga.realtime.chat.dto.ChatSendRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** {@link ChatService} 검증. DB·WebSocket 브로커 없이 도는 순수 로직 테스트다 (CLAUDE.md §7). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);
    private static final Clock CLOCK =
            Clock.fixed(
                    NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private MatchHistoryPort matchHistoryPort;
    @Mock private UserPort userPort;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService =
                new ChatService(
                        chatMessageRepository, matchHistoryPort, userPort, messagingTemplate, CLOCK);
        given(userPort.findById(1L))
                .willReturn(Optional.of(new UserSummary(1L, "후문호랑이", Gender.M, UserStatus.ACTIVE)));
    }

    @Nested
    @DisplayName("발신")
    class Send {

        @Test
        @DisplayName("멤버면 저장하고 방 전체에 broadcast 한다")
        void savesAndBroadcasts() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            given(chatMessageRepository.save(any()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            chatService.send(17L, 1L, new ChatSendRequest("TEXT", "안녕하세요"));

            ArgumentCaptor<ChatMessageResponse> captor =
                    ArgumentCaptor.forClass(ChatMessageResponse.class);
            verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/chat/17"), captor.capture());
            assertThat(captor.getValue().senderNickname()).isEqualTo("후문호랑이");
            assertThat(captor.getValue().content()).isEqualTo("안녕하세요");
            assertThat(captor.getValue().type()).isEqualTo("TEXT");
        }

        @Test
        @DisplayName("멤버가 아니면 GROUP_NOT_MEMBER — 저장하지 않는다")
        void rejectsNonMember() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(false);

            assertThatThrownBy(() -> chatService.send(17L, 1L, new ChatSendRequest("TEXT", "안녕")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_NOT_MEMBER);

            verify(chatMessageRepository, never()).save(any());
        }

        @Test
        @DisplayName("TEXT·QUICK 이 아니면 INVALID_INPUT")
        void rejectsUnknownType() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);

            assertThatThrownBy(
                            () -> chatService.send(17L, 1L, new ChatSendRequest("SYSTEM", "안녕")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("빈 내용은 INVALID_INPUT")
        void rejectsBlankContent() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);

            assertThatThrownBy(() -> chatService.send(17L, 1L, new ChatSendRequest("TEXT", "  ")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("500자를 넘으면 INVALID_INPUT")
        void rejectsTooLongContent() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            String tooLong = "가".repeat(501);

            assertThatThrownBy(() -> chatService.send(17L, 1L, new ChatSendRequest("TEXT", tooLong)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("지난 메시지 조회")
    class RecentMessages {

        @Test
        @DisplayName("멤버면 오래된 것부터 시간순으로 돌려준다")
        void returnsOldestFirst() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            ChatMessage newer = ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "나중", NOW);
            ChatMessage older =
                    ChatMessage.from(17L, 1L, ChatMessageType.TEXT, "먼저", NOW.minusMinutes(5));
            // 저장소는 최신순으로 준다고 가정한다
            given(
                            chatMessageRepository
                                    .findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                                            org.mockito.ArgumentMatchers.eq(17L),
                                            any(),
                                            any(PageRequest.class)))
                    .willReturn(List.of(newer, older));

            List<ChatMessageResponse> result = chatService.recentMessages(17L, 1L, null, null);

            assertThat(result).extracting(ChatMessageResponse::content)
                    .containsExactly("먼저", "나중");
        }

        @Test
        @DisplayName("시스템 메시지는 발신자 닉네임을 '시스템'으로 채운다")
        void systemMessageHasFixedNickname() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            ChatMessage system = ChatMessage.system(17L, "매칭이 성사됐어요", NOW);
            given(
                            chatMessageRepository
                                    .findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                                            org.mockito.ArgumentMatchers.eq(17L),
                                            any(),
                                            any(PageRequest.class)))
                    .willReturn(List.of(system));

            List<ChatMessageResponse> result = chatService.recentMessages(17L, 1L, null, null);

            assertThat(result.get(0).senderNickname()).isEqualTo("시스템");
        }

        @Test
        @DisplayName("멤버가 아니면 GROUP_NOT_MEMBER")
        void rejectsNonMember() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(false);

            assertThatThrownBy(() -> chatService.recentMessages(17L, 1L, null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_NOT_MEMBER);
        }

        @Test
        @DisplayName("size 범위를 벗어나면 1~100 사이로 자른다")
        void clampsPageSize() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            given(
                            chatMessageRepository
                                    .findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                                            any(), any(), any(PageRequest.class)))
                    .willReturn(List.of());

            chatService.recentMessages(17L, 1L, null, 1000);

            ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
            verify(chatMessageRepository)
                    .findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                            org.mockito.ArgumentMatchers.eq(17L), any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        }
    }
}
