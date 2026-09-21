package com.gachiga.realtime.chat;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.matching.MatchHistoryPort;
import com.gachiga.contract.user.UserPort;
import com.gachiga.realtime.chat.dto.ChatMessageResponse;
import com.gachiga.realtime.chat.dto.ChatSendRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 송수신 (T1-11, FR-20).
 *
 * <p>그룹 구성원인지는 항상 {@link MatchHistoryPort} 로 확인한다. SUBSCRIBE 는 {@code
 * realtime.ChatSubscriptionInterceptor}(T1-9)가 이미 막지만, 발신(SEND)은 별도 채널이라 여기서
 * 다시 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final String TOPIC_PREFIX = "/topic/chat/";
    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final ChatMessageRepository chatMessageRepository;
    private final MatchHistoryPort matchHistoryPort;
    private final UserPort userPort;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    /** {@code /app/chat/{groupId}} 로 들어온 메시지를 저장하고 방 전체에 broadcast 한다 */
    @Transactional
    public void send(Long groupId, Long senderId, ChatSendRequest request) {
        requireMember(groupId, senderId);
        ChatMessageType type = requireSendableType(request.type());
        String content = requireContent(request.content());

        ChatMessage saved =
                chatMessageRepository.save(
                        ChatMessage.from(
                                groupId, senderId, type, content, LocalDateTime.now(clock)));

        broadcast(saved, nicknameOf(senderId));
        log.info("채팅 발신 groupId={} senderId={} type={}", groupId, senderId, type);
    }

    /** 재접속 시 지난 메시지 (FR-20). 멤버만 조회할 수 있다 */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> recentMessages(
            Long groupId, Long userId, LocalDateTime before, Integer size) {
        requireMember(groupId, userId);

        LocalDateTime cutoff = before != null ? before : LocalDateTime.now(clock).plusSeconds(1);
        int pageSize = normalizePageSize(size);

        List<ChatMessage> messages =
                chatMessageRepository.findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        groupId, cutoff, PageRequest.of(0, pageSize));

        Map<Long, String> nicknames = nicknamesOf(messages);
        return messages.stream()
                // 저장소는 최신순으로 주므로, "오래된 것부터 시간순"이 되도록 뒤집는다 (api-spec)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(message -> ChatMessageResponse.of(message, nicknames.get(message.getSenderId())))
                .toList();
    }

    private void broadcast(ChatMessage message, String senderNickname) {
        messagingTemplate.convertAndSend(
                TOPIC_PREFIX + message.getGroupId(),
                ChatMessageResponse.of(message, senderNickname));
    }

    private Map<Long, String> nicknamesOf(List<ChatMessage> messages) {
        Map<Long, String> nicknames = new HashMap<>();
        for (ChatMessage message : messages) {
            Long senderId = message.getSenderId();
            if (senderId.equals(ChatMessage.SYSTEM_SENDER_ID) || nicknames.containsKey(senderId)) {
                continue;
            }
            nicknames.put(senderId, nicknameOf(senderId));
        }
        return nicknames;
    }

    private String nicknameOf(Long senderId) {
        return userPort.findById(senderId).map(user -> user.nickname()).orElse(UNKNOWN_NICKNAME);
    }

    private void requireMember(Long groupId, Long userId) {
        if (!matchHistoryPort.isMember(groupId, userId)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_MEMBER);
        }
    }

    private ChatMessageType requireSendableType(String type) {
        if ("TEXT".equals(type)) {
            return ChatMessageType.TEXT;
        }
        if ("QUICK".equals(type)) {
            return ChatMessageType.QUICK;
        }
        throw new BusinessException(
                ErrorCode.INVALID_INPUT, "메시지 종류는 TEXT 또는 QUICK 만 가능합니다.");
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지 내용을 입력해 주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "메시지는 " + MAX_CONTENT_LENGTH + "자 이내로 입력해 주세요.");
        }
        return content;
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
