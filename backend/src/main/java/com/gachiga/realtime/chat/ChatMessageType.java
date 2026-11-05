package com.gachiga.realtime.chat;

/** 채팅 메시지 종류 (PRD §7.1 {@code chat_messages.type}). */
public enum ChatMessageType {
    /** 자유 입력 메시지 */
    TEXT,
    /** 허용된 문구 3종 중 하나 (FR-22, P1) */
    QUICK,
    /** 서버가 만드는 안내 메시지. 그룹 확정·해체 등 (FR-20) */
    SYSTEM
}
