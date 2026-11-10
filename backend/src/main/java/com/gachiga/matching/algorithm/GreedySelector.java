package com.gachiga.matching.algorithm;

import com.gachiga.matching.domain.MatchCandidate;
import java.util.*;

/**
 * T1-6: 그리디 선택 (PRD §5.1 Step 4)
 * 
 * 점수 높은 조합부터 선택.
 * 겹치는 요청이 있으면 스킵.
 */
public class GreedySelector {

    /**
     * 그리디 선택
     * 
     * @param candidates 평가된 조합 리스트 (정렬 필요)
     * @return 선택된 조합들 (요청이 겹치지 않음)
     */
    public static List<MatchCandidate> select(List<MatchCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }

        // 점수순 정렬 (높은순)
        List<MatchCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Long.compare(b.score(), a.score()));

        List<MatchCandidate> selected = new ArrayList<>();
        Set<Long> usedRequests = new HashSet<>();

        // 그리디 선택
        for (MatchCandidate candidate : sorted) {
            // 겹치는 요청 확인
            boolean hasConflict = candidate.requestIds().stream()
                .anyMatch(usedRequests::contains);

            if (!hasConflict) {
                selected.add(candidate);
                usedRequests.addAll(candidate.requestIds());
            }
        }

        return selected;
    }
}