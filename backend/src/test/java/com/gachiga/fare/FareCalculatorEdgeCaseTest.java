package com.gachiga.fare;

import static org.junit.jupiter.api.Assertions.*;

import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteResult;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FareCalculator 엣지 케이스")
class FareCalculatorEdgeCaseTest {

    @Test
    @DisplayName("엣지: 누구도 soloFare보다 더 내면 안 된다")
    void testNoRiderPaysMoreThanSoloFare() {
        // soloFare 합(12,000) >= 총 요금(10,000) 이라 동승이 이득인 조합
        RouteResult route = new RouteResult(
                10000,
                3000,
                300,
                List.of(new RouteResult.Section(3000, 300, emptyPath())),
                false,
                null
        );

        List<FareCalculator.RiderInfo> riders = List.of(
                new FareCalculator.RiderInfo(1L, 1000, 1000),
                new FareCalculator.RiderInfo(2L, 1000, 9000),
                new FareCalculator.RiderInfo(3L, 1000, 2000)
        );

        Map<Long, Integer> shares = FareCalculator.calculate(route, riders);

        int total = shares.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(10000, total);

        for (FareCalculator.RiderInfo rider : riders) {
            assertTrue(
                    shares.get(rider.userId()) <= rider.soloFare(),
                    "userId=" + rider.userId() + " 분담 " + shares.get(rider.userId())
                            + " > soloFare " + rider.soloFare());
        }
    }

    private List<Coordinate> emptyPath() {
        return List.of();
    }
}