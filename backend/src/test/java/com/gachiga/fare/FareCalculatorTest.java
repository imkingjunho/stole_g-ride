package com.gachiga.fare;

import static org.junit.jupiter.api.Assertions.*;

import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteResult;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FareCalculator 정산 로직")
class FareCalculatorTest {

    /**
     * PRD §5.2 3인 예시 검증
     * - 총 요금 15,000원
     * - 예상 분담: 1,500 / 3,750 / 9,750원
     */
    @Test
    @DisplayName("3인 기본 예시: 총 15,000원 → 1,500 / 3,750 / 9,750")
    void testThreeRidersExample() {
        // Given
        RouteResult route = new RouteResult(
                15000,  // totalFare
                20000,  // totalDistance (m)
                600,    // totalDuration (s)
                List.of(
                        new RouteResult.Section(5000, 150, emptyPath()),
                        new RouteResult.Section(5000, 150, emptyPath()),
                        new RouteResult.Section(10000, 300, emptyPath())
                ),
                false,  // estimated
                null    // rawJson
        );

        List<FareCalculator.RiderInfo> riders = List.of(
                new FareCalculator.RiderInfo(1L, 2000, 1500),    // A: 2km → ~1,500원
                new FareCalculator.RiderInfo(2L, 5000, 3750),    // B: 5km → ~3,750원
                new FareCalculator.RiderInfo(3L, 10000, 9750)    // C: 10km → ~9,750원
        );

        // When
        Map<Long, Integer> shares = FareCalculator.calculate(route, riders);

        // Then
        assertEquals(1500, shares.get(1L));  // A
        assertEquals(3750, shares.get(2L));  // B
        assertEquals(9750, shares.get(3L));  // C

        // 합계 검증: 반드시 totalFare와 일치
        int total = shares.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(15000, total);
    }

    /**
     * 2인 케이스: 정산 합계 보존 검증
     */
    @Test
    @DisplayName("2인 케이스: 총 10,000원 분할")
    void testTwoRiders() {
        // Given
        RouteResult route = new RouteResult(
                10000,  // totalFare
                10000,  // totalDistance
                300,    // totalDuration
                List.of(new RouteResult.Section(10000, 300, emptyPath())),
                false,
                null
        );

        List<FareCalculator.RiderInfo> riders = List.of(
                new FareCalculator.RiderInfo(1L, 3000, 3000),
                new FareCalculator.RiderInfo(2L, 7000, 7000)
        );

        // When
        Map<Long, Integer> shares = FareCalculator.calculate(route, riders);

        // Then
        int total = shares.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(route.totalFare(), total);
        assertEquals(3000, shares.get(1L));
        assertEquals(7000, shares.get(2L));
    }

    /**
     * 4인 케이스: 100원 단위 올림 및 합계 보존
     */
    @Test
    @DisplayName("4인 케이스: 100원 단위 올림 후 합계 = 15,000원")
    void testFourRidersWithRounding() {
        // Given
        RouteResult route = new RouteResult(
                15000,
                20000,
                600,
                List.of(new RouteResult.Section(20000, 600, emptyPath())),
                false,
                null
        );

        List<FareCalculator.RiderInfo> riders = List.of(
                new FareCalculator.RiderInfo(1L, 3333, 3333),
                new FareCalculator.RiderInfo(2L, 3333, 3333),
                new FareCalculator.RiderInfo(3L, 3333, 3333),
                new FareCalculator.RiderInfo(4L, 10001, 10000)
        );

        // When
        Map<Long, Integer> shares = FareCalculator.calculate(route, riders);

        // Then
        int total = shares.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(15000, total, "100원 단위 올림 후 합계는 정확히 총 요금과 일치");

        // 각 라이더의 분담액은 100원 단위
        for (Integer share : shares.values()) {
            assertEquals(0, share % 100, "모든 분담액은 100원 단위");
        }
    }

    /**
     * 정산 합계 보존 (합계 검증)
     * Σ(share_i) = totalFare
     */
    @Test
    @DisplayName("정산 합계 보존: 모든 분담액의 합 = 총 요금")
    void testFareSumPreservation() {
        // Given
        RouteResult route = new RouteResult(
                18500,  // 랜덤 요금
                25000,
                750,
                List.of(new RouteResult.Section(25000, 750, emptyPath())),
                false,
                null
        );

        List<FareCalculator.RiderInfo> riders = List.of(
                new FareCalculator.RiderInfo(10L, 2500, 2500),
                new FareCalculator.RiderInfo(20L, 4200, 4200),
                new FareCalculator.RiderInfo(30L, 6800, 6800),
                new FareCalculator.RiderInfo(40L, 11500, 11500)
        );

        // When
        Map<Long, Integer> shares = FareCalculator.calculate(route, riders);

        // Then
        int totalShare = shares.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(route.totalFare(), totalShare, "정산 합계는 정확히 총 요금과 일치");
    }

    /**
     * 절감액 계산
     */
    @Test
    @DisplayName("절감액 계산: soloFare - share")
    void testSavingsCalculation() {
        // Given
        Map<Long, Integer> shares = Map.of(
                1L, 1500,
                2L, 3750,
                3L, 9750
        );

        List<FareCalculator.RiderInfo> riders = List.of(
                new FareCalculator.RiderInfo(1L, 2000, 1500),
                new FareCalculator.RiderInfo(2L, 5000, 5000),   // 절감액 = 5000 - 3750 = 1250
                new FareCalculator.RiderInfo(3L, 10000, 10000)  // 절감액 = 10000 - 9750 = 250
        );

        // When
        Map<Long, Integer> savings = FareCalculator.calculateSavings(shares, riders);

        // Then
        assertEquals(0, savings.get(1L));     // 손해 0원 (절감 안 함)
        assertEquals(1250, savings.get(2L));  // B: 1,250원 절감
        assertEquals(250, savings.get(3L));   // C: 250원 절감
    }

    /**
     * 잘못된 입력 처리
     */
    @Test
    @DisplayName("예외: 라이더 없음")
    void testEmptyRiders() {
        RouteResult route = new RouteResult(15000, 20000, 600, List.of(), false, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> FareCalculator.calculate(route, List.of())
        );
    }

    @Test
    @DisplayName("예외: 총 요금 0원 이하")
    void testInvalidTotalFare() {
        RouteResult route = new RouteResult(0, 20000, 600, List.of(), false, null);

        List<FareCalculator.RiderInfo> riders = List.of(
                new FareCalculator.RiderInfo(1L, 10000, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> FareCalculator.calculate(route, riders)
        );
    }

    @Test
    @DisplayName("예외: RiderInfo 생성 시 userId null")
    void testNullUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FareCalculator.RiderInfo(null, 5000, 5000)
        );
    }

    @Test
    @DisplayName("예외: RiderInfo 생성 시 soloDistance 음수")
    void testNegativeSoloDistance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FareCalculator.RiderInfo(1L, -100, 5000)
        );
    }

    // ===== 헬퍼 메서드 =====

    private List<Coordinate> emptyPath() {
        return List.of();
    }
}