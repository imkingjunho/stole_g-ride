package com.gachiga.matching.algorithm;

import static org.assertj.core.api.Assertions.*;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.contract.route.RouteResult;

import org.junit.jupiter.api.Test;

import java.util.*;

class FareCalculatorTest {
    private RouteResult route(int fare) {
        return new RouteResult(fare, 10000, 600, List.of(), false, null);
    }

    private FareCalculator.RiderInfo rider(long id, int distance, int cap) {
        return new FareCalculator.RiderInfo(id, distance, cap);
    }

    @Test
    void prdExample() {
        var riders = List.of(rider(1, 3000, 5600), rider(2, 6000, 8200), rider(3, 10000, 12400));
        var shares = FareCalculator.calculate(route(15000), riders);
        assertThat(shares).isEqualTo(Map.of(1L, 2368, 2L, 4737, 3L, 7895));
        assertThat(FareCalculator.calculateSavings(shares, riders))
                .isEqualTo(Map.of(1L, 3232, 2L, 3463, 3L, 4505));
    }

    @Test
    void cappedAmountsAreRedistributed() {
        var riders = List.of(rider(1, 1000, 1000), rider(2, 1000, 9000), rider(3, 1000, 2000));
        assertThat(FareCalculator.calculate(route(10000), riders))
                .isEqualTo(Map.of(1L, 1000, 2L, 7000, 3L, 2000));
    }

    @Test
    void equalRemaindersUseUserIdNotInputOrder() {
        var riders = List.of(rider(3, 1000, 10), rider(2, 1000, 10), rider(1, 1000, 10));
        assertThat(FareCalculator.calculate(route(5), riders))
                .isEqualTo(Map.of(1L, 2, 2L, 2, 3L, 1));
    }

    @Test
    void exactCapsAndZeroCapacity() {
        assertThat(FareCalculator.calculate(route(10), List.of(rider(1, 1, 0), rider(2, 1, 10))))
                .isEqualTo(Map.of(1L, 0, 2L, 10));
    }

    @Test
    void largeIntegerAmountsDoNotOverflow() {
        var riders =
                List.of(
                        rider(1, Integer.MAX_VALUE, Integer.MAX_VALUE),
                        rider(2, Integer.MAX_VALUE, Integer.MAX_VALUE),
                        rider(3, Integer.MAX_VALUE, Integer.MAX_VALUE),
                        rider(4, Integer.MAX_VALUE, Integer.MAX_VALUE));
        var result = FareCalculator.calculate(route(Integer.MAX_VALUE), riders);
        assertThat(result.values().stream().mapToLong(Integer::longValue).sum())
                .isEqualTo(Integer.MAX_VALUE);
        assertThat(result.values()).allMatch(value -> value >= 0);
    }

    @Test
    void randomInputsPreserveTotalCapsAndOrderIndependence() {
        Random random = new Random(42);
        for (int trial = 0; trial < 1000; trial++) {
            List<FareCalculator.RiderInfo> riders = new ArrayList<>();
            for (int i = 0, count = 2 + random.nextInt(3); i < count; i++)
                riders.add(rider(i + 1, 1 + random.nextInt(100000), random.nextInt(50000)));
            int cap = riders.stream().mapToInt(FareCalculator.RiderInfo::soloFare).sum();
            int fare = 1 + random.nextInt(cap);
            var result = FareCalculator.calculate(route(fare), riders);
            assertThat(result.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(fare);
            riders.forEach(r -> assertThat(result.get(r.userId())).isBetween(0, r.soloFare()));
            Collections.shuffle(riders, random);
            assertThat(FareCalculator.calculate(route(fare), riders)).isEqualTo(result);
        }
    }

    @Test
    void infeasibleOrInvalidInputsAreRejected() {
        assertThatThrownBy(
                        () ->
                                FareCalculator.calculate(
                                        route(11), List.of(rider(1, 1, 5), rider(2, 1, 5))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FareCalculator.calculate(route(1), List.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FareCalculator.calculate(route(0), List.of(rider(1, 1, 1))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                FareCalculator.calculate(
                                        route(2), List.of(rider(1, 1, 5), rider(1, 2, 5))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> rider(1, 0, 5)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new FareCalculator.RiderInfo(null, 1, 1))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                FareCalculator.calculateSavings(
                                        Map.of(1L, 11), List.of(rider(1, 1, 10))))
                .isInstanceOf(BusinessException.class);
    }
}
