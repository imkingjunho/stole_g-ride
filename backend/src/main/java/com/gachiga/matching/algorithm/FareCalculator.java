package com.gachiga.matching.algorithm;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.route.RouteResult;

import java.util.*;

/** 단독 이동 거리 비율 정산. 단독요금 상한과 원 단위 합계 보존을 지킨다 (PRD §5.2). */
public final class FareCalculator {
    private FareCalculator() {}

    public static Map<Long, Integer> calculate(RouteResult route, List<RiderInfo> riders) {
        if (route == null
                || route.totalFare() <= 0
                || riders == null
                || riders.isEmpty()
                || riders.size() > 4
                || new HashSet<>(riders.stream().map(RiderInfo::userId).toList()).size()
                        != riders.size()) {
            throw invalid();
        }
        long capacity = riders.stream().mapToLong(RiderInfo::soloFare).sum();
        if (capacity < route.totalFare()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "단독요금 합보다 동승 요금이 큽니다.");
        }
        List<RiderInfo> pending = new ArrayList<>(riders);
        Map<Long, Integer> result = new LinkedHashMap<>();
        long remaining = route.totalFare();
        while (!pending.isEmpty()) {
            long weightSum = pending.stream().mapToLong(RiderInfo::soloDistance).sum();
            long roundFare = remaining;
            // 곱셈은 long: int 요금 × int 거리의 최댓값도 long 안에 들어간다.
            List<RiderInfo> capped =
                    pending.stream()
                            .filter(
                                    r -> {
                                        long numerator = roundFare * r.soloDistance();
                                        return numerator / weightSum > r.soloFare()
                                                || (numerator / weightSum == r.soloFare()
                                                        && numerator % weightSum > 0);
                                    })
                            .toList();
            if (!capped.isEmpty()) {
                for (RiderInfo r : capped) {
                    result.put(r.userId(), r.soloFare());
                    remaining -= r.soloFare();
                }
                pending.removeAll(capped);
                continue;
            }
            long assigned = 0;
            for (RiderInfo r : pending) {
                int share = (int) (remaining * r.soloDistance() / weightSum);
                result.put(r.userId(), share);
                assigned += share;
            }
            // 내림 잔액은 소수 잔여 내림차순, 동률은 userId 오름차순으로 1원씩 배분한다.
            pending.sort(
                    Comparator.<RiderInfo>comparingLong(
                                    r -> roundFare * r.soloDistance() % weightSum)
                            .reversed()
                            .thenComparing(RiderInfo::userId));
            int remainder = (int) (remaining - assigned);
            for (int i = 0; i < remainder; i++) {
                RiderInfo r = pending.get(i);
                result.compute(r.userId(), (id, value) -> value + 1);
            }
            break;
        }
        return Map.copyOf(result);
    }

    public static Map<Long, Integer> calculateSavings(
            Map<Long, Integer> shares, List<RiderInfo> riders) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (RiderInfo r : riders) {
            Integer share = shares.get(r.userId());
            if (share == null || share < 0 || share > r.soloFare()) throw invalid();
            result.put(r.userId(), r.soloFare() - share);
        }
        return Map.copyOf(result);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "정산 입력값을 확인해 주세요.");
    }

    public record RiderInfo(Long userId, int soloDistance, int soloFare) {
        public RiderInfo {
            if (userId == null || userId <= 0 || soloDistance <= 0 || soloFare < 0) throw invalid();
        }
    }
}
