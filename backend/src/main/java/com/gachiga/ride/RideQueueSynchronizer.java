package com.gachiga.ride;

import com.gachiga.contract.event.RideRequestCancelled;
import com.gachiga.contract.event.RideRequestCreated;
import com.gachiga.contract.event.RideRequestExpired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DB 와 Redis 대기열을 맞춰 준다.
 *
 * <p><b>왜 이벤트로 하나:</b> 서비스 안에서 바로 Redis 를 건드리면, 그 뒤 트랜잭션이 롤백됐을 때
 * DB 에는 없는 요청이 대기열에 남는다. {@code AFTER_COMMIT} 으로 받으면 <b>커밋된 것만</b>
 * 반영되므로 그런 유령이 생기지 않는다 (roles T1-4 "트랜잭션 커밋 후 Redis 반영").
 *
 * <p>수신 측은 절대 예외를 밖으로 내보내지 않는다. 대기열 갱신이 실패해도 요청 자체는 이미
 * 커밋돼 유효하며, 어긋난 색인은 {@link RideQueue#rebuild} 로 복구한다 (CLAUDE.md §3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideQueueSynchronizer {

    private final RideQueue rideQueue;
    private final RideRequestRepository rideRequestRepository;

    /** 요청이 생기면 대기열에 넣는다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RideRequestCreated event) {
        safely(
                "등록",
                event.requestId(),
                () ->
                        rideRequestRepository
                                .findById(event.requestId())
                                .ifPresent(
                                        request ->
                                                rideQueue.add(
                                                        request.getHubId(),
                                                        request.getId(),
                                                        request.getDepartAt())));
    }

    /** 사용자가 취소하면 대기열에서 뺀다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RideRequestCancelled event) {
        removeFromQueue("취소", event.requestId());
    }

    /** 만료되면 대기열에서 뺀다 (FR-10) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RideRequestExpired event) {
        removeFromQueue("만료", event.requestId());
    }

    /**
     * 그 밖의 상태 전이(매칭 배정·확정·완료·해체 뒤 재대기) 뒤에 대기열을 지금 상태에 맞춘다.
     *
     * <p>전이마다 넣을지 뺄지를 따로 정하지 않고 <b>현재 상태만 본다</b> — WAITING 이면 넣고 아니면
     * 뺀다. 같은 신호가 두 번 와도 결과가 같다.
     *
     * <p>{@code fallbackExecution} 을 켠 이유: {@code matching} 이 트랜잭션 없이
     * {@code tryMarkMatched} 를 부르면(그 메서드가 자기 트랜잭션을 연다) 여기까지 신호가 안 올 수
     * 있다. 켜 두면 트랜잭션이 없을 때 곧바로 처리한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(RideRequestStatusChanged event) {
        safely(
                "상태 변경",
                event.requestId(),
                () ->
                        rideRequestRepository
                                .findById(event.requestId())
                                .ifPresent(
                                        request -> {
                                            if (request.getStatus() == RideRequestStatus.WAITING) {
                                                rideQueue.add(
                                                        request.getHubId(),
                                                        request.getId(),
                                                        request.getDepartAt());
                                            } else {
                                                rideQueue.remove(request.getHubId(), request.getId());
                                            }
                                        }));
    }

    /** 거점 id 를 알아야 키를 만들 수 있으므로 요청을 다시 읽는다 */
    private void removeFromQueue(String reason, Long requestId) {
        safely(
                reason,
                requestId,
                () ->
                        rideRequestRepository
                                .findById(requestId)
                                .ifPresent(
                                        request ->
                                                rideQueue.remove(
                                                        request.getHubId(), request.getId())));
    }

    private void safely(String reason, Long requestId, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException e) {
            // 발행 측 트랜잭션은 이미 커밋됐다. 여기서 터져도 요청은 유효하다
            log.error("대기열 동기화 실패 ({}) requestId={}", reason, requestId, e);
        }
    }
}
