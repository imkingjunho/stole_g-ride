package com.gachiga.matching.domain;

import java.util.List;
import java.util.Map;

/**
 * 議고빀 ?됯? ?꾨낫 (媛?媛앹껜)
 */
public record MatchCandidate(
    List<Long> requestIds,           // 議고빀 ?붿껌 ID??
    long savingSum,                  // 珥??덇컧??
    double avgDetourRatio,           // ?됯퇏 ?고쉶??
    int maxWaitTime,                 // 理쒕? ?湲??쒓컙(珥?
    long score,                      // 理쒖쥌 ?먯닔
    Map<Long, Integer> shares,       // userId -> shareAmount
    Map<Long, Integer> savings       // userId -> savingAmount
) {
    public int size() {
        return requestIds.size();
    }
}