package com.gachiga.fare;

import com.gachiga.contract.route.RouteResult;
import java.util.*;

/**
 * 구간 분할 정산 계산 기계(PRD §5.2, FR-16, FR-17).
 *
 * <p><b>정산 방식</b>
 * 1. 각 rider의 soloDistance 비율로 임시 요금 계산
 * 2. 각 rider의 최종 분담 = min(계산값, soloFare)
 * 3. 마지막 rider는 남은 금액으로 보정
 */
public class FareCalculator {

    public static Map<Long, Integer> calculate(
            RouteResult route,
            List<RiderInfo> riders) {

        if (riders == null || riders.isEmpty()) {
            throw new IllegalArgumentException("riders는 최소 1명 이상이어야 합니다");
        }

        if (route.totalFare() <= 0) {
            throw new IllegalArgumentException("totalFare는 0보다 커야 합니다");
        }

        // 1단계: 전체 soloDistance 합산
        long totalSoloDistance = riders.stream()
                .mapToLong(RiderInfo::soloDistance)
                .sum();

        if (totalSoloDistance <= 0) {
            throw new IllegalArgumentException("전체 거리는 0보다 커야 합니다");
        }

        // 2단계: 각 rider의 임시 분담 요금 계산 (soloDistance 비율)
        Map<Long, Long> tempShares = new LinkedHashMap<>();
        for (RiderInfo rider : riders) {
            long tempShare = Math.round(
                    (double) route.totalFare() * rider.soloDistance() / totalSoloDistance);
            tempShares.put(rider.userId(), tempShare);
        }

        // 3단계: 최종 분담 = min(임시 분담, soloFare)
        Map<Long, Integer> shares = new LinkedHashMap<>();
        long accumulatedFare = 0;

        for (int i = 0; i < riders.size(); i++) {
            RiderInfo rider = riders.get(i);
            long tempShare = tempShares.get(rider.userId());
            int finalShare;

            if (i == riders.size() - 1) {
                // 마지막 rider: 남은 금액으로 보정
                finalShare = (int) (route.totalFare() - accumulatedFare);
            } else {
                // min(tempShare, soloFare)
                finalShare = (int) Math.min(tempShare, rider.soloFare());
            }

            shares.put(rider.userId(), finalShare);
            accumulatedFare += finalShare;
        }

        return shares;
    }

    public static Map<Long, Integer> calculateSavings(
            Map<Long, Integer> shares,
            List<RiderInfo> riders) {

        Map<Long, Integer> savings = new HashMap<>();

        for (RiderInfo rider : riders) {
            int share = shares.getOrDefault(rider.userId(), 0);
            int saving = Math.max(0, rider.soloFare() - share);
            savings.put(rider.userId(), saving);
        }

        return savings;
    }

    public record RiderInfo(
            Long userId,
            int soloDistance,
            int soloFare) {

        public RiderInfo {
            if (userId == null) throw new IllegalArgumentException("userId는 null이 아니어야 합니다");
            if (soloDistance < 0) throw new IllegalArgumentException("soloDistance는 0 이상이어야 합니다");
            if (soloFare < 0) throw new IllegalArgumentException("soloFare는 0 이상이어야 합니다");
        }
    }
}