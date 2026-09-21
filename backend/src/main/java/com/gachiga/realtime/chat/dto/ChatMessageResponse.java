package com.gachiga.realtime.chat.dto;

import com.gachiga.realtime.chat.ChatMessage;
import java.time.LocalDateTime;

/**
 * 채팅 메시지 응답. {@code docs/api-spec.yaml} 의 {@code ChatMessage} 와 1:1 이다.
 *
 * <p>시스템 메시지의 {@code senderNickname} 은 고정 문구를 쓴다.
 */
public record ChatMessageResponse(
        Long id,
        Long groupId,
        Long senderId,
        String senderNickname,
        String type,
        String content,
        LocalDateTime createdAt) {

    private static final String SYSTEM_NICKNAME = "시스템";

    public static ChatMessageResponse of(ChatMessage message, String senderNickname) {
        return new ChatMessageResponse(
                message.getId(),
                message.getGroupId(),
                message.getSenderId(),
                message.getSenderId().equals(ChatMessage.SYSTEM_SENDER_ID)
                        ? SYSTEM_NICKNAME
                        : senderNickname,
                message.getType().name(),
                message.getContent(),
                message.getCreatedAt());
    }
}
