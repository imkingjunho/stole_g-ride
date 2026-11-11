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

    public int size() {
        return riders.size();
    }
}
