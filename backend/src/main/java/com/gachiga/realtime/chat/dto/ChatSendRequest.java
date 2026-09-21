package com.gachiga.realtime.chat.dto;

/**
 * {@code /app/chat/{groupId}} 로 들어오는 발신 본문 (PRD §14.5).
 *
 * @param type {@code TEXT} 또는 {@code QUICK} 만 허용한다. {@code SYSTEM} 은 서버만 만든다
 */
public record ChatSendRequest(String type, String content) {}
