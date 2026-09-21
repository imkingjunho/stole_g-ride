package com.gachiga.realtime;

import com.gachiga.contract.ride.QueueStatusPort;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기 상태 push — 주기편 (T2-1, FR-09, PRD §14.5).
 *
 * <p>5초마다 현재 STOMP 로 연결된 사용자 전원의 대기 상태를 {@link QueueStatusPort} 로 조회해
 * {@code /user/queue/status} 로 push 한다. 진행 중인 요청이 없는 사용자는 조용히 건너뛴다.
 *
 * <p>변경 즉시 push 는 {@link QueueStatusEventListener} 가 맡는다 — 5초를 기다리지 않고 바로
 * 알려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueStatusPushScheduler {

    private final SimpUserRegistry simpUserRegistry;
    private final QueueStatusPort queueStatusPort;
    private final QueueStatusPusher pusher;

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    public void pushToConnectedUsers() {
        for (SimpUser user : simpUserRegistry.getUsers()) {
            pushOne(user.getName());
        }
    }

    private void pushOne(String userIdText) {
        Long userId = parseUserId(userIdText);
        if (userId == null) {
            return;
        }
        try {
            queueStatusPort.statusOf(userId).ifPresent(status -> pusher.push(userId, status));
        } catch (RuntimeException e) {
            log.warn("대기 상태 push 실패 userId={}", userId, e);
        }
    }

    private Long parseUserId(String name) {
        try {
            return Long.valueOf(name);
        } catch (NumberFormatException e) {
            log.warn("[대기 상태 push] 사용자 id 형식이 아니다: {}", name);
            return null;
        }
    }
}
