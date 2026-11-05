package com.gachiga.ride;

import com.gachiga.contract.event.RideRequestExpired;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기 시간이 지난 요청을 정리한다 (FR-10).
 *
 * <p>주기는 {@code gachiga.ride.expiry-interval-seconds}(기본 30초)다. 매칭 tick 과 같은
 * 주기로 돌지만, 매칭이 만료 30초 전 요청을 이미 제외하므로(E-04) 둘이 같은 요청을 두고
 * 다투지 않는다.
 *
 * <p>만료된 요청은 EXPIRED 가 되고 대기열에서 빠지며, {@link RideRequestExpired} 로 알린다.
 * 대기열 제거는 {@code RideQueueSynchronizer} 가 커밋 뒤에 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestExpiryScheduler {

    private final RideRequestRepository rideRequestRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 만료 처리 한 번.
     *
     * <p>{@code fixedDelayString} 은 이전 실행이 <b>끝난 뒤부터</b> 센다. DB 가 느릴 때
     * 실행이 겹쳐 같은 요청을 두 번 만료시키는 일을 막는다.
     */
    @Scheduled(
            fixedDelayString = "${gachiga.ride.expiry-interval-seconds}",
            timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    @Transactional
    public void expireOverdueRequests() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<RideRequest> overdue =
                rideRequestRepository.findByStatusAndExpiresAtLessThanEqual(
                        RideRequestStatus.WAITING, now);
        if (overdue.isEmpty()) {
            return;
        }

        for (RideRequest request : overdue) {
            request.expire();
            eventPublisher.publishEvent(
                    new RideRequestExpired(request.getId(), request.getUserId()));
        }

        log.info("대기 시간 만료 처리 {}건", overdue.size());
    }
}
