package com.gachiga.matching.algorithm;

import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.RouteProvider;
import com.gachiga.contract.route.RouteResult;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.user.UserPort;
import com.gachiga.fare.FareCalculator;
import com.gachiga.matching.domain.MatchCandidate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * T1-5: 조합 평가 (PRD §5.1 Step 3)
 * 
 * 각 조합에 대해:
 * 1. 경로 조회
 * 2. 정산 계산
 * 3. 수용 조건 검사
 * 4. 점수 계산
 */
public class CombinationEvaluator {

    private final RouteProvider routeProvider;
    private final double w1;  // 절감액 가중치
    private final double w2;  // 우회율 가중치
    private final double w3;  // 대기시간 가중치

    public CombinationEvaluator(RouteProvider routeProvider, double w1, double w2, double w3) {
        this.routeProvider = routeProvider;
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
    }

    /**
     * 조합 평가
     * 
     * @param combination 조합된 요청 리스트
     * @return 평가 결과 (null이면 거절)
     */
    public MatchCandidate evaluate(List<WaitingRequest> combination) {
        if (combination.size() < 2 || combination.size() > 4) {
            return null;
        }

        try {
            // 1단계: waypoints 순서 결정 (거리순 휴리스틱)
            List<Coordinate> waypoints = determineWaypoints(combination);

            // 2단계: RouteProvider 호출
            Coordinate origin = combination.get(0).destination();
            Coordinate destination = combination.get(combination.size() - 1).destination();
            
            RouteResult route = routeProvider.findRoute(origin, waypoints, destination);
            if (route == null) {
                return null;
            }

            // 3단계: 정산 계산
            List<FareCalculator.RiderInfo> riders = toRiderInfos(combination);
            Map<Long, Integer> shares = FareCalculator.calculate(route, riders);
            Map<Long, Integer> savings = FareCalculator.calculateSavings(shares, riders);

            // 4단계: 수용 조건 검사
            if (!isAcceptable(combination, shares, savings, route)) {
                return null;
            }

            // 5단계: 점수 계산
            long savingSum = savings.values().stream().mapToLong(Integer::longValue).sum();
            double avgDetourRatio = calculateAvgDetourRatio(combination, route);
            int maxWaitTime = calculateMaxWaitTime(combination);

            long score = calculateScore(savingSum, avgDetourRatio, maxWaitTime);

            return new MatchCandidate(
                combination.stream().map(WaitingRequest::requestId).collect(Collectors.toList()),
                savingSum,
                avgDetourRatio,
                maxWaitTime,
                score
            );

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * waypoints 순서 결정 (거리순 휴리스틱)
     * 첫 번째 요청의 목적지 → 나머지 목적지들을 거리순으로 정렬
     */
    private List<Coordinate> determineWaypoints(List<WaitingRequest> combination) {
        if (combination.size() == 2) {
            return new ArrayList<>();  // waypoints 없음 (원점 → 목적지)
        }

        Coordinate first = combination.get(0).destination();
        List<Coordinate> rest = combination.subList(1, combination.size())
            .stream()
            .map(WaitingRequest::destination)
            .sorted((c1, c2) -> Double.compare(
                haversineDistance(first.lat(), first.lng(), c1.lat(), c1.lng()),
                haversineDistance(first.lat(), first.lng(), c2.lat(), c2.lng())
            ))
            .collect(Collectors.toList());

        return rest.subList(0, rest.size() - 1);  // 마지막 1개는 destination
    }

    /**
     * 수용 조건 검사
     */
    private boolean isAcceptable(List<WaitingRequest> combination, 
                                 Map<Long, Integer> shares,
                                 Map<Long, Integer> savings,
                                 RouteResult route) {
        // 조건 1: 각 사용자가 최소한 약간의 절감액
        for (Integer saving : savings.values()) {
            if (saving < 0) {
                return false;
            }
        }

        // 조건 2: 우회율 초과 확인
        double avgDetourRatio = calculateAvgDetourRatio(combination, route);
        double maxDetourRatio = combination.stream()
            .mapToDouble(r -> r.maxDetourRatio().doubleValue())
            .max()
            .orElse(0.30);
        
        if (avgDetourRatio > maxDetourRatio) {
            return false;
        }

        return true;
    }

    /**
     * 평균 우회율 계산
     */
    private double calculateAvgDetourRatio(List<WaitingRequest> combination, RouteResult route) {
        double totalDetourRatio = 0;
        
        for (WaitingRequest req : combination) {
            int sharedDistance = route.totalDistance() - req.soloDistance();
            if (req.soloDistance() > 0) {
                totalDetourRatio += (double) sharedDistance / req.soloDistance();
            }
        }

        return combination.size() > 0 ? totalDetourRatio / combination.size() : 0;
    }

    /**
     * 최대 대기시간 계산 (분 단위)
     */
    private int calculateMaxWaitTime(List<WaitingRequest> combination) {
        long minCreatedAt = combination.stream()
            .map(WaitingRequest::createdAt)
            .min(java.time.LocalDateTime::compareTo)
            .orElse(java.time.LocalDateTime.now())
            .toEpochSecond(java.time.ZoneOffset.UTC);

        long maxCreatedAt = combination.stream()
            .map(WaitingRequest::createdAt)
            .max(java.time.LocalDateTime::compareTo)
            .orElse(java.time.LocalDateTime.now())
            .toEpochSecond(java.time.ZoneOffset.UTC);

        return (int) ((maxCreatedAt - minCreatedAt) / 60);
    }

    /**
     * 점수 계산
     * score = w1 × saving + w2 × (1 - avgDetour) + w3 × (1 - waitTime/300)
     */
    private long calculateScore(long savingSum, double avgDetourRatio, int maxWaitTime) {
        double scoreDouble = 
            w1 * savingSum +
            w2 * (1.0 - Math.min(avgDetourRatio, 1.0)) +
            w3 * (1.0 - Math.min(maxWaitTime / 300.0, 1.0));

        return Math.round(scoreDouble * 1000);  // 정수로 변환 (소수점 3자리 유지)
    }

    /**
     * WaitingRequest → RiderInfo 변환
     */
    private List<FareCalculator.RiderInfo> toRiderInfos(List<WaitingRequest> requests) {
        return requests.stream()
            .map(r -> new FareCalculator.RiderInfo(r.userId(), r.soloDistance(), r.soloFare()))
            .collect(Collectors.toList());
    }

    /**
     * Haversine 공식
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_METERS = 6371000;

        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.asin(Math.sqrt(a));
        return EARTH_RADIUS_METERS * c;
    }
}