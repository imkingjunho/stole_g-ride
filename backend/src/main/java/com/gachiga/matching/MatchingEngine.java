package com.gachiga.matching;

import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.HubPort;
import com.gachiga.matching.algorithm.Clustering;
import com.gachiga.matching.algorithm.CombinationEvaluator;
import com.gachiga.matching.algorithm.CombinationGenerator;
import com.gachiga.matching.algorithm.GreedySelector;
import com.gachiga.matching.domain.MatchCandidate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 새 요청, 두 번째 요청의 1분 기한, 복구 주기마다 대기열을 다시 읽고 그룹별로 배정한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngine {
    private final RideRequestPort rideRequestPort;
    private final CombinationEvaluator evaluator;
    private final GroupService groupService;
    private final Clock clock;

    /** 거점 좌표(방향 버킷·직선 우회 사전 추정용). 없으면 기존 3km 목적지 연쇄로 동작한다 */
    @Autowired(required = false)
    private HubPort hubPort;

    @Value("${gachiga.matching.max-evaluations-per-tick:60}")
    private int maxEvaluations = 60;

    private LocalDateTime nextPairDeadline;

    @Scheduled(fixedDelay = 1000)
    public synchronized void checkPairDeadline() {
        if (nextPairDeadline != null && !LocalDateTime.now(clock).isBefore(nextPairDeadline)) {
            executeTick();
        }
    }

    private record ReadyGroup(List<WaitingRequest> riders, LocalDateTime readyAt, double excess) {}

    @Scheduled(fixedDelayString = "${gachiga.matching.tick-millis:30000}")
    public synchronized void executeTick() {
        var now = LocalDateTime.now(clock);
        var waiting = rideRequestPort.findWaiting();
        // 거점 좌표를 알면 출발 방향으로 묶고, 카카오 조회 전에 직선 우회 추정으로 거른다.
        Map<Long, Coordinate> origins = new HashMap<>();
        if (hubPort != null && waiting.size() >= 2) {
            for (Long hubId : waiting.stream().map(WaitingRequest::hubId).collect(Collectors.toSet())) {
                hubPort.findById(hubId).ifPresent(h -> origins.put(hubId, new Coordinate(h.lat(), h.lng())));
            }
        }
        var combinations =
                CombinationGenerator.generateCombinations(
                        Clustering.cluster(waiting, origins.isEmpty() ? null : origins::get));
        var ready = new ArrayList<ReadyGroup>();
        nextPairDeadline = null;
        for (var riders : combinations) {
            Coordinate origin = origins.get(riders.get(0).hubId());
            double excess =
                    origin == null
                            ? 0
                            : CombinationEvaluator.straightLineWorstExcess(origin, riders);
            if (excess > 0) continue;
            var lastArrival =
                    riders.stream()
                            .map(WaitingRequest::createdAt)
                            .max(Comparator.naturalOrder())
                            .orElseThrow();
            var readyAt = riders.size() == 2 ? lastArrival.plusMinutes(1) : lastArrival;
            if (!readyAt.isAfter(now)) {
                ready.add(new ReadyGroup(riders, readyAt, excess));
            } else if (nextPairDeadline == null || readyAt.isBefore(nextPairDeadline)) {
                nextPairDeadline = readyAt;
            }
        }
        // 평가 예산이 소진될 때는 먼저 매칭 가능해진 조합부터 확인한다.
        ready.sort(
                Comparator.comparing(ReadyGroup::readyAt)
                        .thenComparing(group -> group.riders().size() == 2)
                        .thenComparingDouble(ReadyGroup::excess));
        var candidates = new ArrayList<MatchCandidate>();
        Map<Long, LocalDateTime> pairDeadlines = new HashMap<>();
        for (var group : ready.stream().limit(maxEvaluations).toList()) {
            try {
                var candidate = evaluator.evaluate(group.riders());
                if (candidate == null) continue;
                candidates.add(candidate);
                if (candidate.size() == 2) {
                    for (var id : candidate.requestIds()) {
                        pairDeadlines.merge(id, group.readyAt(), (a, b) -> a.isBefore(b) ? a : b);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("매칭 조합 평가 실패", e);
            }
        }

        for (var candidate : GreedySelector.select(candidates, pairDeadlines)) {
            try {
                groupService.create(candidate);
            } catch (RuntimeException e) {
                log.warn("매칭 그룹 저장 실패 — 해당 그룹 배정은 롤백된다", e);
            }
        }
    }
}
