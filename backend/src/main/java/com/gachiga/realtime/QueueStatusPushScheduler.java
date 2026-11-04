package com.gachiga.realtime;

import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p>5초마다 현재 STOMP로 연결된 사용자 전원의 대기 상태를 {@link QueueStatusPort#statusOfAll}로 한
 * 번에 조회해 {@code /user/queue/status}로 push한다. 연결 인원마다 {@link QueueStatusPort#statusOf}를
 * 부르면 쿼리가 인원 수만큼 나가므로 배치 조회를 쓴다(T2-3). 진행 중인 요청이 없는 사용자는 조용히
 * 건너뛴다.
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
        List<Long> userIds = connectedUserIds();
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, QueueStatus> statuses;
        try {
            statuses = queueStatusPort.statusOfAll(userIds);
        } catch (RuntimeException e) {
            log.warn("대기 상태 일괄 조회 실패 대상 인원={}", userIds.size(), e);
            return;
        }
        statuses.forEach(pusher::push);
    }

    private List<Long> connectedUserIds() {
        List<Long> userIds = new ArrayList<>();
        for (SimpUser user : simpUserRegistry.getUsers()) {
            Long userId = parseUserId(user.getName());
            if (userId != null) {
                userIds.add(userId);
            }
        }
        return userIds;
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
