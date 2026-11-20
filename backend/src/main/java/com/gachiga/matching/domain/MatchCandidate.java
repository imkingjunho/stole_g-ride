package com.gachiga.matching.domain;

import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.RouteResult;

import java.util.List;
import java.util.Map;

/** 평가한 경로와 정산을 함께 보관하여 저장 단계에서 재호출하지 않는다. */
public record MatchCandidate(
        List<WaitingRequest> riders,
        HubInfo hub,
        RouteResult route,
        Map<Long, Integer> shares,
        Map<Long, Integer> savings,
        Map<Long, Integer> sharedDistances,
        Map<Long, Integer> dropoffOrders,
        double avgDetourRatio,
        long score) {
    public List<Long> requestIds() {
        return riders.stream().map(WaitingRequest::requestId).toList();
    }

    /** 같은 두 요청에 다시 제안하지 않도록, 생성 순서에 무관한 키를 쓴다. */
    public static String pairOfferKey(List<WaitingRequest> riders) {
        if (riders.size() != 2 || !Integer.valueOf(3).equals(riders.get(0).preferredSize())) return null;
        long a = riders.get(0).requestId(), b = riders.get(1).requestId();
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    public int size() {
        return riders.size();
    }
}
