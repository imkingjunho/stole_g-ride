package com.gachiga.matching;

import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.matching.algorithm.Clustering;
import com.gachiga.matching.algorithm.CombinationEvaluator;
import com.gachiga.matching.algorithm.CombinationGenerator;
import com.gachiga.matching.algorithm.GreedySelector;
import com.gachiga.matching.domain.MatchCandidate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

/** 새 요청, 두 번째 요청의 1분 기한, 복구 주기마다 대기열을 다시 읽고 그룹별로 배정한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngine {
    private final RideRequestPort rideRequestPort;
    private final CombinationEvaluator evaluator;
    private final GroupService groupService;
    private final Clock clock;

    @Value("${gachiga.matching.max-evaluations-per-tick:30}")
    private int maxEvaluations = 30;

    private LocalDateTime nextPairDeadline;

    @Scheduled(fixedDelay = 1000)
    public synchronized void checkPairDeadline() {
        if (nextPairDeadline != null && !LocalDateTime.now(clock).isBefore(nextPairDeadline)) {
            executeTick();
        }
    }

    private record ReadyGroup(List<WaitingRequest> riders, LocalDateTime readyAt) {}

    @Scheduled(fixedDelayString = "${gachiga.matching.tick-millis:30000}")
    public synchronized void executeTick() {
        var now = LocalDateTime.now(clock);
        var combinations =
                CombinationGenerator.generateCombinations(
                        Clustering.cluster(rideRequestPort.findWaiting()));
        var ready = new ArrayList<ReadyGroup>();
        nextPairDeadline = null;
        for (var riders : combinations) {
            var lastArrival =
                    riders.stream()
                            .map(WaitingRequest::createdAt)
                            .max(Comparator.naturalOrder())
                            .orElseThrow();
            var readyAt = riders.size() == 2 ? lastArrival.plusMinutes(1) : lastArrival;
            if (!readyAt.isAfter(now)) {
                ready.add(new ReadyGroup(riders, readyAt));
            } else if (nextPairDeadline == null || readyAt.isBefore(nextPairDeadline)) {
                nextPairDeadline = readyAt;
            }
        }
        // 평가 예산이 소진될 때는 먼저 매칭 가능해진 조합부터 확인한다.
        ready.sort(
                Comparator.comparing(ReadyGroup::readyAt)
                        .thenComparing(group -> group.riders().size() == 2));
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
