package com.gachiga.matching;

import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.matching.algorithm.*;
import com.gachiga.matching.domain.MatchCandidate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/** 경로 평가는 트랜잭션 밖에서, 조건부 배정과 그룹 저장은 그룹별 트랜잭션으로 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngine {
    private final RideRequestPort rideRequestPort;
    private final CombinationEvaluator evaluator;
    private final GroupService groupService;

    @Value("${gachiga.matching.max-evaluations-per-tick:30}")
    private int maxEvaluations = 30;

    @Scheduled(fixedDelayString = "${gachiga.matching.tick-millis:30000}")
    public synchronized void executeTick() {
        var combinations =
                CombinationGenerator.generateCombinations(
                        Clustering.cluster(rideRequestPort.findWaiting()));
        var candidates = new ArrayList<MatchCandidate>();
        for (var combination : combinations.stream().limit(maxEvaluations).toList()) {
            try {
                var candidate = evaluator.evaluate(combination);
                if (candidate != null) candidates.add(candidate);
            } catch (RuntimeException e) {
                log.warn("매칭 조합 평가 실패", e);
            }
        }
        for (var candidate : GreedySelector.select(candidates)) {
            try {
                groupService.create(candidate);
            } catch (RuntimeException e) {
                log.warn("매칭 그룹 저장 실패 — 해당 그룹 배정은 롤백된다", e);
            }
        }
    }
}
