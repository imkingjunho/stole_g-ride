package com.gachiga.fare;

import com.gachiga.contract.route.RouteResult;
import java.util.*;

/**
 * 구간 분할 정산 계산 기계 (PRD §5.2, FR-16, FR-17).
 *
 * <p><b>정산 방식</b>
 * 1. 각 rider의 soloDistance 비율로 임시 요금 계산
 * 2. soloFare를 넘는 rider는 soloFare로 고정
 * 3. 고정된 금액을 뺀 나머지를 남은 rider끼리 다시 나눔 (더 넘는 사람이 없을 때까지 반복)
 * 4. 반올림 오차는 마지막 rider가 흡수
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

        long totalSoloDistance = riders.stream()
                .mapToLong(RiderInfo::soloDistance)
                .sum();

        if (totalSoloDistance <= 0) {
            throw new IllegalArgumentException("전체 거리는 0보다 커야 합니다");
        }

        long totalSoloFare = riders.stream()
                .mapToLong(RiderInfo::soloFare)
                .sum();

        if (totalSoloFare < route.totalFare()) {
            throw new IllegalArgumentException("soloFare 합이 총 요금보다 작아 동승 이득이 없습니다");
        }

        int n = riders.size();
        long[] share = new long[n];
        boolean[] capped = new boolean[n];
        long remainingFare = route.totalFare();

        // 상한(soloFare)을 넘는 사람이 더 없을 때까지 반복
        boolean changed = true;
        while (changed) {
            changed = false;

            long weightSum = 0;
            for (int i = 0; i < n; i++) {
                if (!capped[i]) {
                    weightSum += weight(riders.get(i));
                }
            }
            if (weightSum == 0) {
                break;
            }

            long roundFare = remainingFare; // 이번 라운드 기준 금액 (라운드 도중 바뀌지 않게 고정)
            for (int i = 0; i < n; i++) {
                if (capped[i]) {
                    continue;
                }
                RiderInfo rider = riders.get(i);
                double proportional = (double) roundFare * weight(rider) / weightSum;
                if (proportional > rider.soloFare()) {
                    share[i] = rider.soloFare();
                    capped[i] = true;
                    remainingFare -= rider.soloFare();
                    changed = true;
                }
            }
        }

        // 고정되지 않은 사람들에게 남은 금액을 나눔
        long weightSum = 0;
        int lastUncapped = -1;
        for (int i = 0; i < n; i++) {
            if (!capped[i]) {
                weightSum += weight(riders.get(i));
                lastUncapped = i;
            }
        }

        long assigned = 0;
        for (int i = 0; i < n; i++) {
            if (capped[i]) {
                continue;
            }
            if (i == lastUncapped) {
                share[i] = remainingFare - assigned;
            } else {
                share[i] = Math.round((double) remainingFare * weight(riders.get(i)) / weightSum);
                assigned += share[i];
            }
        }

        Map<Long, Integer> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            shares.put(riders.get(i).userId(), (int) share[i]);
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

    // soloDistance가 0인 rider도 계산에서 빠지지 않도록 최소 1로 취급
    private static long weight(RiderInfo rider) {
        return Math.max(1L, rider.soloDistance());
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