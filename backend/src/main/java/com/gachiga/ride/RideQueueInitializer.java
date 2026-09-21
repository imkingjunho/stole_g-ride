package com.gachiga.ride;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기동할 때 DB 를 기준으로 대기열을 다시 만든다.
 *
 * <p><b>왜 필요한가:</b> Redis 가 재시작되거나 비워지면 대기열이 사라진다. DB 에는 대기 중인
 * 요청이 그대로 남아 있으므로, 그 상태로 두면 매칭이 아무도 못 찾는다.
 * 기동 시 한 번 맞춰 주면 "DB 와 항상 일치" 가 선언이 아니라 사실이 된다 (roles T1-4).
 *
 * <p>운영 중에 어긋난 것을 발견하면 이 클래스를 참고해 같은 방식으로 복구하면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideQueueInitializer {

    private final RideRequestRepository rideRequestRepository;
    private final RideQueue rideQueue;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void rebuildQueues() {
        try {
            List<RideRequest> waiting =
                    rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                            RideRequestStatus.WAITING, java.time.LocalDateTime.now());
            if (waiting.isEmpty()) {
                log.info("대기 중인 요청이 없어 대기열 재구성을 건너뛴다");
                return;
            }

            Map<Long, List<RideRequest>> byHub =
                    waiting.stream().collect(Collectors.groupingBy(RideRequest::getHubId));
            byHub.forEach(rideQueue::rebuild);

            log.info("대기열 재구성 완료 — 거점 {}곳, 요청 {}건", byHub.size(), waiting.size());
        } catch (RuntimeException e) {
            // 재구성에 실패해도 서버는 떠야 한다. 대기열은 색인일 뿐이다
            log.error("기동 시 대기열 재구성 실패 — 매칭이 요청을 못 찾을 수 있다", e);
        }
    }
}
