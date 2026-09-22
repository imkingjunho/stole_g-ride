package com.gachiga.matching;

import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.RouteProvider;
import com.gachiga.contract.user.UserPort;
import com.gachiga.matching.algorithm.*;
import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.matching.domain.GroupStatus;
import com.gachiga.matching.domain.MatchMember;
import com.gachiga.matching.port.MatchGroupRepository;
import com.gachiga.matching.port.MatchMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MatchingEngine {

    private final RideRequestPort rideRequestPort;
    private final RouteProvider routeProvider;
    private final UserPort userPort;
    private final MatchGroupRepository groupRepository;
    private final MatchMemberRepository memberRepository;

    private double w1 = 1.0;
    private double w2 = 0.5;
    private double w3 = 0.3;

    @Scheduled(fixedRate = 30000)
    public void executeTick() {
        long startTime = System.currentTimeMillis();

        try {
            log.info("=== Matching TICK Started ===");

            List<WaitingRequest> waiting = rideRequestPort.findWaiting();
            if (waiting.isEmpty()) {
                log.info("No waiting requests");
                return;
            }

            List<List<WaitingRequest>> clusters = Clustering.cluster(waiting);
            List<List<WaitingRequest>> combinations = CombinationGenerator.generateCombinations(clusters);

            CombinationEvaluator evaluator = new CombinationEvaluator(routeProvider, w1, w2, w3);
            List<com.gachiga.matching.domain.MatchCandidate> candidates = new ArrayList<>();
            for (List<WaitingRequest> combo : combinations) {
                var candidate = evaluator.evaluate(combo);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }

            var selected = GreedySelector.select(candidates);
            log.info("Selected {} groups", selected.size());

            // T1-8: 그룹 생성 및 자동 성사
            for (var candidate : selected) {
                // candidate에서 requestIds 추출하여 원래 WaitingRequest 찾기
                // 임시로 waiting 리스트에서 찾음
                List<WaitingRequest> combination = new ArrayList<>();
                for (Long requestId : candidate.requestIds()) {
                    waiting.stream()
                        .filter(r -> r.requestId().equals(requestId))
                        .findFirst()
                        .ifPresent(combination::add);
                }
                if (combination.size() == candidate.requestIds().size()) {
                    createAndConfirmGroup(combination);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("=== Matching TICK Completed in {}ms ===", duration);

        } catch (Exception e) {
            log.error("Error in matching tick", e);
        }
    }

    /**
     * 그룹 생성 및 자동 성사
     */
    private void createAndConfirmGroup(List<WaitingRequest> combination) {
        // 1. MatchGroup 생성
        MatchGroup group = MatchGroup.builder()
            .hubId(combination.get(0).hubId())
            .totalFare(10000)
            .totalDistance(5000)
            .totalDuration(600)
            .estimated(false)
            .status(GroupStatus.PENDING)
            .build();

        MatchGroup saved = groupRepository.save(group);
        log.info("Created group {}", saved.getId());

        // 2. 멤버 추가
        for (int i = 0; i < combination.size(); i++) {
            WaitingRequest req = combination.get(i);
            MatchMember member = MatchMember.builder()
                .group(saved)
                .requestId(req.requestId())
                .userId(req.userId())
                .boardingOrder(i)
                .dropoffOrder(i)
                .sharedDistance(0)
                .shareAmount(0)
                .savingAmount(0)
                .accepted(false)
                .build();
            memberRepository.save(member);
        }

        // 3. CONFIRMED로 변경
        saved.confirm();
        groupRepository.save(saved);
        log.info("Group {} confirmed", saved.getId());

        // 4. RideRequestPort 업데이트
        for (WaitingRequest req : combination) {
            rideRequestPort.markConfirmed(req.requestId());
        }
    }
}