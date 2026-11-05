package com.gachiga.realtime.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code chat} 설정 — {@code resources/domain/auth.yml} 의 {@code gachiga.chat.*} 를 읽는다.
 *
 * @param retentionHours 방이 열린 뒤 메시지를 보관하는 시간(FR-21). 이 시간이 지나면 물리 삭제된다
 * @param cleanupIntervalSeconds 삭제 스케줄러가 도는 주기(초)
 */
@ConfigurationProperties(prefix = "gachiga.chat")
public record ChatProperties(int retentionHours, int cleanupIntervalSeconds) {}
