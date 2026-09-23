package com.gachiga.ride;

import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * <p><b>대기자가 없는 거점도 비운다.</b> 서버가 꺼진 사이 끝난 요청이 Redis 에 남아 있을 수 있다.
 * 대기자가 있는 거점만 다시 만들면 나머지 거점의 낡은 키는 영영 지워지지 않는다.
 *
 * <p>운영 중에 어긋난 것을 발견하면 이 클래스를 참고해 같은 방식으로 복구하면 된다.
 *
 * <p>{@code gachiga.ride.rebuild-queue-on-startup=false} 면 뜨지 않는다. 테스트가 그렇게 둔다 — 켜 두면
 * 테스트 컨텍스트가 뜰 때마다 개발용 Redis 의 대기열을 지운다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "gachiga.ride",
        name = "rebuild-queue-on-startup",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class RideQueueInitializer {

    private final RideRequestRepository rideRequestRepository;
    private final RideQueue rideQueue;
    private final HubPort hubPort;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void rebuildQueues() {
        try {
            List<RideRequest> waiting =
                    rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                            RideRequestStatus.WAITING, java.time.LocalDateTime.now());
            Map<Long, List<RideRequest>> byHub =
                    waiting.stream().collect(Collectors.groupingBy(RideRequest::getHubId));

            Set<Long> hubIds = new LinkedHashSet<>(allHubIds());
            hubIds.addAll(byHub.keySet());
            int failed = 0;
            for (Long hubId : hubIds) {
                // 대기자가 없으면 빈 목록으로 다시 만든다 — 키를 지우는 것과 같다
                if (!rideQueue.rebuild(hubId, byHub.getOrDefault(hubId, List.of()))) {
                    failed++;
                }
            }

            if (failed == 0) {
                log.info("대기열 재구성 완료 — 거점 {}곳, 대기 요청 {}건", hubIds.size(), waiting.size());
            } else {
                log.error(
                        "대기열 재구성 일부 실패 — 거점 {}곳 중 {}곳 실패 (Redis 가 떠 있는지 확인)",
                        hubIds.size(),
                        failed);
            }
        } catch (RuntimeException e) {
            // 재구성에 실패해도 서버는 떠야 한다. 대기열은 색인일 뿐이다
            log.error("기동 시 대기열 재구성 실패 — 매칭이 요청을 못 찾을 수 있다", e);
        }
    }

    /**
     * 모든 거점 id. 거점 목록을 못 읽어도 대기자가 있는 거점은 재구성해야 하므로 여기서 삼킨다.
     */
    private List<Long> allHubIds() {
        try {
            return hubPort.findAll().stream().map(HubInfo::id).toList();
        } catch (RuntimeException e) {
            log.warn("거점 목록을 읽지 못해 대기자가 있는 거점만 재구성한다", e);
            return List.of();
        }
    }
}
