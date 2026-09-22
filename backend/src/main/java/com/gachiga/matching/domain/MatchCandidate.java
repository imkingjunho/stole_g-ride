package com.gachiga.matching.domain;

import java.util.List;

/**
 * 조합 평가 후보 (값 객체)
 */
public record MatchCandidate(
    List<Long> requestIds,      // 조합 요청 ID들
    long savingSum,             // 총 절감액
    double avgDetourRatio,      // 평균 우회율
    int maxWaitTime,            // 최대 대기 시간(초)
    long score                  // 최종 점수
) {
    public int size() {
        return requestIds.size();
    }
}