package com.gachiga.realtime;

import com.gachiga.contract.event.RideRequestCancelled;
import com.gachiga.contract.event.RideRequestCreated;
import com.gachiga.contract.event.RideRequestExpired;
import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 대기열 변화가 생기면 5초를 기다리지 않고 바로 push 한다 (T2-1, FR-09).
 *
 * <p>이승민의 {@code ride} 가 발행하고 여기서 받는다. <b>수신 측은 절대 예외를 밖으로 내보내지
 * 않는다</b>(CLAUDE.md §3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueStatusEventListener {

    private final QueueStatusPort queueStatusPort;
    private final QueueStatusPusher pusher;

    /** 새로 대기열에 들어간 직후의 상태를 바로 보여준다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RideRequestCreated event) {
        try {
            queueStatusPort
                    .statusOf(event.userId())
                    .ifPresent(status -> pusher.push(event.userId(), status));
        } catch (RuntimeException e) {
            log.error("대기 상태 즉시 push 실패 userId={}", event.userId(), e);
        }
    }

    /**
     * 취소됐다.
     *
     * <p>취소된 요청은 더 이상 "진행 중"이 아니라서 {@code QueueStatusPort} 로 다시 조회해도 값이
     * 없다. 그래서 종료 상태를 이벤트에 실려 온 정보만으로 직접 만들어 보낸다 — 대기 화면이 즉시
     * 닫히려면 이 push 가 필요하다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RideRequestCancelled event) {
        pushTerminal(event.requestId(), event.userId(), "CANCELLED");
    }

    /** 만료됐다 (FR-10). 취소와 같은 이유로 직접 만들어 보낸다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RideRequestExpired event) {
        pushTerminal(event.requestId(), event.userId(), "EXPIRED");
    }

    private void pushTerminal(Long requestId, Long userId, String status) {
        try {
            pusher.push(userId, new QueueStatus(requestId, status, 0, 0));
        } catch (RuntimeException e) {
            log.error("대기 상태 즉시 push 실패 userId={} status={}", userId, status, e);
        }
    }
}
