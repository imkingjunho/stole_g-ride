package com.gachiga.matching.algorithm;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.util.GeoUtils;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.*;
import com.gachiga.contract.user.*;
import com.gachiga.matching.domain.MatchCandidate;
import com.gachiga.matching.filter.HardFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** 거점 출발 경로를 한 번 평가하고 개인별 우회 한도와 단독요금 상한을 검사한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CombinationEvaluator {
    private final RouteProvider routeProvider;
    private final HubPort hubPort;
    private final UserPort userPort;
    private final Clock clock;

    @Value("${gachiga.matching.weights.w1:1.0}")
    private double w1 = 1.0;

    @Value("${gachiga.matching.weights.w2:0.5}")
    private double w2 = 0.5;

    @Value("${gachiga.matching.weights.w3:0.3}")
    private double w3 = 0.3;

    public MatchCandidate evaluate(List<WaitingRequest> combination) {
        if (combination.size() < 2 || combination.size() > 4) return null;
        LocalDateTime now = LocalDateTime.now(clock);
        for (int i = 0; i < combination.size(); i++) {
            WaitingRequest a = combination.get(i);
            if (!a.expiresAt().isAfter(now.plusSeconds(30))
                    || a.soloDistance() <= 0
                    || userPort.findById(a.userId())
                            .filter(u -> u.status() == UserStatus.ACTIVE)
                            .isEmpty()) return null;
            for (int j = i + 1; j < combination.size(); j++) {
                if (!HardFilter.compatible(a, combination.get(j), userPort)) return null;
            }
        }
        HubInfo hub = hubPort.findById(combination.get(0).hubId()).orElse(null);
        if (hub == null) return null;
        Coordinate origin = new Coordinate(hub.lat(), hub.lng());
        List<WaitingRequest> ordered =
                combination.stream()
                        .sorted(
                                Comparator.<WaitingRequest>comparingDouble(
                                                r -> distance(origin, r.destination()))
                                        .thenComparing(WaitingRequest::requestId))
                        .toList();
        List<Coordinate> stops = new ArrayList<>();
        Map<Long, Integer> orders = new LinkedHashMap<>();
        for (WaitingRequest request : ordered) {
            // E-07: 연속된 100m 이내 하차지는 하나의 정차 지점으로 묶는다. 정산 가중치는 단독 거리다.
            if (stops.isEmpty()
                    || distance(stops.get(stops.size() - 1), request.destination()) > 100) {
                stops.add(request.destination());
            }
            orders.put(request.userId(), stops.size());
        }
        RouteResult route =
                routeProvider.findRoute(
                        origin, stops.subList(0, stops.size() - 1), stops.get(stops.size() - 1));
        if (route == null || route.totalFare() <= 0 || route.sections().size() != stops.size())
            return null;
        Map<Long, Integer> sharedDistances = new LinkedHashMap<>();
        double detourSum = 0;
        for (WaitingRequest request : ordered) {
            int shared =
                    route.sections().subList(0, orders.get(request.userId())).stream()
                            .mapToInt(RouteResult.Section::distance)
                            .sum();
            double detour =
                    Math.max(
                            0, (shared - (double) request.soloDistance()) / request.soloDistance());
            if (detour > request.maxDetourRatio().doubleValue()) return null;
            sharedDistances.put(request.userId(), shared);
            detourSum += detour;
        }
        var riders =
                ordered.stream()
                        .map(
                                r ->
                                        new FareCalculator.RiderInfo(
                                                r.userId(), r.soloDistance(), r.soloFare()))
                        .toList();
        try {
            var shares = FareCalculator.calculate(route, riders);
            var savings = FareCalculator.calculateSavings(shares, riders);
            double averageDetour = detourSum / ordered.size();
            long waitedSeconds =
                    ordered.stream()
                            .mapToLong(
                                    r ->
                                            Math.max(
                                                    0,
                                                    Duration.between(r.createdAt(), now)
                                                            .getSeconds()))
                            .max()
                            .orElse(0);
            double score =
                    w1 * savings.values().stream().mapToLong(Integer::longValue).sum()
                            + w2 * (1 - averageDetour)
                            + w3 * Math.min(waitedSeconds / 300.0, 1.0);
            return new MatchCandidate(
                    ordered,
                    hub,
                    route,
                    shares,
                    savings,
                    sharedDistances,
                    orders,
                    averageDetour,
                    Math.round(score * 1000));
        } catch (BusinessException e) {
            log.debug("수용할 수 없는 정산 조합: {}", e.getMessage());
            return null;
        }
    }

    private static double distance(Coordinate a, Coordinate b) {
        return GeoUtils.haversineMeters(a.lat(), a.lng(), b.lat(), b.lng());
    }
}
