package com.gachiga.ride;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupDissolved;
import com.gachiga.contract.ride.RideRequestPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 그룹 쪽 사건을 받아 요청 상태를 정리한다 (PRD §14.2).
 *
 * <p>서준의 {@code matching} 이 발행하고 여기서 받는다. 직접 호출이 아니라 이벤트인 이유는
 * 두 모듈이 서로를 몰라도 되게 하기 위해서다.
 *
 * <p><b>이벤트는 사용자 id 만 싣는다.</b> 요청 id 가 아니므로, 사용자별 진행 중 요청을 찾아
 * 상태를 바꾼다. 1인 1건이라 최대 한 건이다.
 *
 * <p>수신 측은 절대 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3). 여기서 터지면 이미 커밋된
 * 그룹 확정이 반쯤 반영된 상태로 남는데, 그래도 발행 측을 되돌릴 수는 없기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideGroupEventListener {

    private static final List<RideRequestStatus> IN_PROGRESS =
            List.of(RideRequestStatus.WAITING, RideRequestStatus.MATCHED, RideRequestStatus.CONFIRMED);

    private final RideRequestRepository rideRequestRepository;

    /** 그룹이 확정됐다 → 요청도 CONFIRMED */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupConfirmed event) {
        apply(event.userIds(), RideRequest::markConfirmed, "그룹 확정", event.groupId());
    }

    /**
     * 그룹이 해체됐다 → 다시 대기열로 (E-01).
     *
     * <p>남은 대기 시간은 유지한다. 해체가 사용자 잘못이 아닌데 시간을 깎으면 억울하다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupDissolved event) {
        apply(event.userIds(), RideRequest::markWaiting, "그룹 해체", event.groupId());
    }

    /** 탑승이 끝났다 → COMPLETED */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupCompleted event) {
        apply(event.userIds(), RideRequest::markCompleted, "탑승 완료", event.groupId());
    }

    /**
     * 구성원들의 진행 중 요청에 상태 전이를 적용한다.
     *
     * <p>한 명이 실패해도 나머지는 처리한다. 전부 멈추면 그룹의 절반만 정리된 상태가 되는데,
     * 그보다는 최대한 많이 맞춰 두는 편이 복구하기 쉽다.
     */
    private void apply(
            List<Long> userIds,
            java.util.function.Consumer<RideRequest> transition,
            String label,
            Long groupId) {

        if (userIds == null || userIds.isEmpty()) {
            log.warn("{} 이벤트에 구성원이 없다 groupId={}", label, groupId);
            return;
        }

        int changed = 0;
        for (Long userId : userIds) {
            try {
                var request =
                        rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                                userId, IN_PROGRESS);
                if (request.isEmpty()) {
                    log.warn("{} 처리할 요청이 없다 groupId={} userId={}", label, groupId, userId);
                    continue;
                }
                transition.accept(request.get());
                changed++;
            } catch (RuntimeException e) {
                log.error("{} 처리 실패 groupId={} userId={}", label, groupId, userId, e);
            }
        }
        log.info("{} 반영 groupId={} {}/{}명", label, groupId, changed, userIds.size());
    }
}
