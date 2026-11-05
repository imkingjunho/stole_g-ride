package com.gachiga.ride;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupDissolved;
import com.gachiga.contract.event.GroupProposed;
import com.gachiga.contract.ride.RideRequestPort;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 * <p>그룹 id 도 여기서 요청에 적는다. {@code GET /api/requests/me} 가 그룹을 알려 줘야 하는데
 * ({@code RideRequestDetail.groupId}), 그룹은 {@code matching} 소유라 물어볼 곳이 이벤트뿐이다.
 *
 * <p>수신 측은 절대 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3). 여기서 터지면 이미 커밋된
 * 그룹 확정이 반쯤 반영된 상태로 남는데, 그래도 발행 측을 되돌릴 수는 없기 때문이다.
 *
 * <p>상태를 바꾼 요청마다 {@link RideRequestStatusChanged} 를 내서 대기열을 맞춘다.
 *
 * <p>{@link RideRequestPort} 의 상태 변경 메서드와 겹치는 일을 한다. {@code matching} 이 어느 쪽을
 * 쓰든 요청 상태가 맞도록 두 길을 모두 열어 두었다.
 *
 * <p><b>이벤트는 늦게 오거나 순서가 뒤바뀔 수 있다.</b> 이벤트는 사용자 id 로 "지금 진행 중인 요청" 을 찾기
 * 때문에, 그 요청이 정말 이 그룹 것인지 확인하지 않으면 이미 해체된 그룹이 되살아나거나(확정이 해체 뒤에
 * 도착) 방금 다른 그룹에 배정된 요청이 대기열로 돌아가 두 번 배정된다(해체가 새 배정 뒤에 도착).
 * 그래서 이벤트마다 <b>상태와 그룹 id 가 맞을 때만</b> 적용하고, 아니면 경고만 남긴다.
 * 해체는 그룹 id 가 <b>같을 때만</b> 적용한다 — 그룹을 모르는 MATCHED 요청은 다른 그룹에 막 배정된 것일 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideGroupEventListener {

    private static final List<RideRequestStatus> IN_PROGRESS =
            List.of(RideRequestStatus.WAITING, RideRequestStatus.MATCHED, RideRequestStatus.CONFIRMED);

    /** 그룹에 들어가 있는 상태. 확정·해체·완료 이벤트는 이 상태의 요청에만 적용한다 */
    private static final Set<RideRequestStatus> IN_GROUP =
            EnumSet.of(RideRequestStatus.MATCHED, RideRequestStatus.CONFIRMED);

    private final RideRequestRepository rideRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 그룹이 만들어졌다 → 그룹 id 를 적는다.
     *
     * <p>상태는 바꾸지 않는다. MATCHED 로의 전이는 {@code matching} 이
     * {@link RideRequestPort#tryMarkMatched} 로 먼저 끝낸다 (E-02).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupProposed event) {
        apply(
                event.userIds(),
                request ->
                        request.getStatus() == RideRequestStatus.MATCHED
                                && sameOrUnknownGroup(request, event.groupId()),
                request -> request.assignGroup(event.groupId()),
                "그룹 생성",
                event.groupId());
    }

    /**
     * 그룹이 확정됐다 → 요청도 CONFIRMED.
     *
     * <p>그룹 id 를 여기서도 적는다. {@code GroupProposed} 수신이 실패했어도 확정된 그룹은 찾을 수
     * 있어야 한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupConfirmed event) {
        apply(
                event.userIds(),
                request ->
                        IN_GROUP.contains(request.getStatus())
                                && sameOrUnknownGroup(request, event.groupId()),
                request -> {
                    request.markConfirmed();
                    request.assignGroup(event.groupId());
                },
                "그룹 확정",
                event.groupId());
    }

    /**
     * 그룹이 해체됐다 → 다시 대기열로 (E-01).
     *
     * <p>남은 대기 시간은 유지한다. 해체가 사용자 잘못이 아닌데 시간을 깎으면 억울하다.
     * 그룹 id 는 {@link RideRequest#markWaiting} 이 비운다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupDissolved event) {
        apply(
                event.userIds(),
                request ->
                        IN_GROUP.contains(request.getStatus())
                                && event.groupId() != null
                                && event.groupId().equals(request.getGroupId()),
                RideRequest::markWaiting,
                "그룹 해체",
                event.groupId());
    }

    /** 탑승이 끝났다 → COMPLETED. 그룹 id 는 이력을 위해 남긴다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GroupCompleted event) {
        apply(
                event.userIds(),
                request ->
                        IN_GROUP.contains(request.getStatus())
                                && sameOrUnknownGroup(request, event.groupId()),
                RideRequest::markCompleted,
                "탑승 완료",
                event.groupId());
    }

    /**
     * 구성원들의 진행 중 요청에 상태 전이를 적용한다.
     *
     * <p>한 명이 실패해도 나머지는 처리한다. 전부 멈추면 그룹의 절반만 정리된 상태가 되는데,
     * 그보다는 최대한 많이 맞춰 두는 편이 복구하기 쉽다.
     */
    /** 요청에 적힌 그룹이 없거나 이벤트의 그룹과 같다. 다른 그룹이 적혀 있으면 이 이벤트와 관계없다 */
    private static boolean sameOrUnknownGroup(RideRequest request, Long groupId) {
        return request.getGroupId() == null || request.getGroupId().equals(groupId);
    }

    private void apply(
            List<Long> userIds,
            Predicate<RideRequest> applicable,
            Consumer<RideRequest> transition,
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
                RideRequest target = request.get();
                if (!applicable.test(target)) {
                    // 늦게 왔거나 순서가 뒤바뀐 이벤트다. 적용하면 엉뚱한 그룹 상태가 남는다
                    log.warn(
                            "{} 이벤트를 적용하지 않는다 — 요청 상태가 맞지 않음 groupId={} userId={} requestId={} "
                                    + "status={} requestGroupId={}",
                            label,
                            groupId,
                            userId,
                            target.getId(),
                            target.getStatus(),
                            target.getGroupId());
                    continue;
                }
                transition.accept(target);
                eventPublisher.publishEvent(new RideRequestStatusChanged(target.getId()));
                changed++;
            } catch (RuntimeException e) {
                log.error("{} 처리 실패 groupId={} userId={}", label, groupId, userId, e);
            }
        }
        log.info("{} 반영 groupId={} {}/{}명", label, groupId, changed, userIds.size());
    }
}
