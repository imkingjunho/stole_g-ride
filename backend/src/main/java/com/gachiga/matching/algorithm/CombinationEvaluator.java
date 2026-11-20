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

    /** 직선 우회 사전 추정에 주는 여유분. 도로가 직선보다 돌아가는 경우를 위해 한도 + 10%p 까지 카카오 조회를 허용한다 */
    static final double STRAIGHT_LINE_MARGIN = 0.10;
    /** 카카오 실패로 추정 경로를 쓸 때는 우회 한도를 보수적으로 적용한다 (직선 470m · 도로 2.4km 같은 캠퍼스 구간 대비) */
    static final double ESTIMATED_ROUTE_LIMIT_FACTOR = 0.7;
    /** 최소 절감 금액(원). 기본요금 구간에서 30%여도 1,440원이라 "천 원 아끼려고 앱을 켜지 않는다"는 하한 */
    static final int MIN_SAVING_WON = 1_500;
    /** 단독요금 경계(원). 미만은 30%, 이상(장거리)은 대기 전반 35% → 후반 20% */
    static final int LONG_TRIP_FARE = 10_000;
    static final double SHORT_TRIP_MIN_RATE = 0.30;
    static final double LONG_TRIP_EARLY_MIN_RATE = 0.35;
    static final double LONG_TRIP_LATE_MIN_RATE = 0.20;

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
        // 카카오 경로 조회 전, 직선거리로 각자 우회를 추정해 한도 + 여유분을 넘으면 버린다.
        if (straightLineWorstExcess(origin, combination) > 0) return null;
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
            double limit =
                    request.maxDetourRatio().doubleValue()
                            * (route.estimated() ? ESTIMATED_ROUTE_LIMIT_FACTOR : 1.0);
            if (detour > limit) return null;
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
            double minSavingRate = 1.0;
            for (WaitingRequest r : ordered) {
                int saving = savings.get(r.userId());
                if (saving < requiredSaving(r, now)) return null;
                if (r.soloFare() > 0) minSavingRate = Math.min(minSavingRate, (double) saving / r.soloFare());
            }
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
            // 인원수 → 가장 적게 아끼는 사람의 절감률 → 우회 → 대기 순으로 우선한다.
            double score =
                    1000.0 * ordered.size()
                            + 1000.0 * minSavingRate
                            + 100.0 * (1 - averageDetour)
                            + 10.0 * Math.min(waitedSeconds / 300.0, 1.0);
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

    /**
     * 각자 최소로 아껴야 하는 금액(원).
     * 단독요금 1만 원 미만은 30%, 이상은 대기 시간 전반 35% · 후반 20%. 어느 쪽이든 최소 1,500원.
     */
    static int requiredSaving(WaitingRequest r, LocalDateTime now) {
        double rate;
        if (r.soloFare() < LONG_TRIP_FARE) {
            rate = SHORT_TRIP_MIN_RATE;
        } else {
            Duration total = Duration.between(r.createdAt(), r.expiresAt());
            boolean lateHalf = !now.isBefore(r.createdAt().plus(total.dividedBy(2)));
            rate = lateHalf ? LONG_TRIP_LATE_MIN_RATE : LONG_TRIP_EARLY_MIN_RATE;
        }
        return Math.max((int) Math.ceil(r.soloFare() * rate), MIN_SAVING_WON);
    }

    /**
     * 직선거리로 추정한 각자의 우회율이 (본인 한도 + 여유분)을 얼마나 넘는지 중 최댓값. 0 이하면 카카오 조회 대상이다.
     * 하차 순서와 100m 정차 병합은 {@link #evaluate} 와 같다.
     */
    public static double straightLineWorstExcess(Coordinate origin, List<WaitingRequest> combination) {
        List<WaitingRequest> ordered =
                combination.stream()
                        .sorted(
                                Comparator.<WaitingRequest>comparingDouble(
                                                r -> distance(origin, r.destination()))
                                        .thenComparing(WaitingRequest::requestId))
                        .toList();
        double worst = Double.NEGATIVE_INFINITY;
        double travelled = 0;
        Coordinate last = origin;
        for (WaitingRequest r : ordered) {
            travelled += distance(last, r.destination());
            last = r.destination();
            double solo = distance(origin, r.destination());
            double estimate = solo <= 0 ? 0 : Math.max(0, travelled / solo - 1);
            double excess =
                    estimate - (r.maxDetourRatio().doubleValue() + STRAIGHT_LINE_MARGIN);
            worst = Math.max(worst, excess);
        }
        return worst;
    }

    private static double distance(Coordinate a, Coordinate b) {
        return GeoUtils.haversineMeters(a.lat(), a.lng(), b.lat(), b.lng());
    }
}
