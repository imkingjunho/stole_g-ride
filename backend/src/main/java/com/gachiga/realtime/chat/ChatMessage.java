package com.gachiga.realtime.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지 한 건 (PRD §7.1 {@code chat_messages}).
 *
 * <p>그룹은 {@code Long groupId} 로만 들고 있다 — {@code matching} 의 엔티티를 참조하지 않는다
 * (PRD §7.2). 탑승 완료 또는 개설 3시간 경과 시 스케줄러가 물리 삭제한다(T1-13, FR-21).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chat_messages",
        indexes = {
            // 방 입장 시 "지난 메시지" 조회, 물리 삭제 스케줄러가 함께 쓴다
            @Index(name = "idx_chat_messages_group_created", columnList = "group_id, created_at")
        })
public class ChatMessage {

    /** 시스템 메시지의 발신자 id. 실제 사용자가 아니므로 0을 쓴다 (api-spec {@code ChatMessage.senderId}) */
    public static final Long SYSTEM_SENDER_ID = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 사용자가 보낸 메시지. {@code type} 은 TEXT 또는 QUICK 만 허용한다 */
    public static ChatMessage from(
            Long groupId, Long senderId, ChatMessageType type, String content, LocalDateTime now) {
        if (type == ChatMessageType.SYSTEM) {
            throw new IllegalArgumentException("SYSTEM 메시지는 ChatMessage.system() 으로 만든다.");
        }
        return build(groupId, senderId, type, content, now);
    }

    /** 서버가 만드는 안내 메시지 (T1-12·E-10) */
    public static ChatMessage system(Long groupId, String content, LocalDateTime now) {
        return build(groupId, SYSTEM_SENDER_ID, ChatMessageType.SYSTEM, content, now);
    }

    private static ChatMessage build(
            Long groupId, Long senderId, ChatMessageType type, String content, LocalDateTime now) {
        ChatMessage message = new ChatMessage();
        message.groupId = groupId;
        message.senderId = senderId;
        message.type = type;
        message.content = content;
        message.createdAt = now;
        return message;
    }
}
