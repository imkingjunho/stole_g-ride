package com.gachiga.matching;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.matching.algorithm.CombinationEvaluator;
import com.gachiga.matching.domain.MatchCandidate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

class PairWindowTest {
    private static final Instant SECOND_ARRIVAL = Instant.parse("2026-11-20T07:00:00Z");
    private final Clock clock = mock(Clock.class);
    private final RideRequestPort queue = mock(RideRequestPort.class);
    private final CombinationEvaluator evaluator = mock(CombinationEvaluator.class);
    private final GroupService groups = mock(GroupService.class);
    private final MatchingEngine engine = new MatchingEngine(queue, evaluator, groups, clock);

    private void setTime(int seconds) {
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(SECOND_ARRIVAL.plusSeconds(seconds));
    }

    private WaitingRequest request(long id, int offset) {
        LocalDateTime created =
                LocalDateTime.ofInstant(SECOND_ARRIVAL.plusSeconds(offset), ZoneOffset.UTC);
        return new WaitingRequest(
                id,
                id,
                1L,
                new Coordinate(35.17, 126.92),
                "목적지",
                created,
                created.plusMinutes(10),
                created,
                false,
                BigDecimal.ONE,
                2000,
                5000,
                0);
    }

    private void allowMatches() {
        when(evaluator.evaluate(anyList()))
                .thenAnswer(
                        call -> {
                            List<WaitingRequest> riders = call.getArgument(0);
                            // 점수가 높아도 합류 기한 전에는 두 명을 먼저 확정하면 안 된다.
                            return new MatchCandidate(
                                    riders,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    0,
                                    riders.size() == 2 ? 1000 : 1);
                        });
    }

    @Test
    void pairWaitsUntilSecondArrivalPlusOneMinuteWithoutAnotherRequest() {
        setTime(0);
        var pair = List.of(request(1, -45), request(2, 0));
        when(queue.findWaiting()).thenReturn(pair);
        allowMatches();
        engine.executeTick();
        setTime(59);
        engine.checkPairDeadline();
        verifyNoInteractions(groups);
        setTime(60);
        engine.checkPairDeadline();
        verify(groups).create(argThat(c -> c.riders().equals(pair)));
    }

    @ParameterizedTest
    @CsvSource({"59,3", "60,3", "61,2"})
    void thirdArrivalIsComparedWithOriginalDeadline(int thirdArrival, int expectedSize) {
        setTime(61); // 동시에 받은 이벤트를 약간 늦게 처리한 경우도 포함한다.
        when(queue.findWaiting())
                .thenReturn(List.of(request(1, -30), request(2, 0), request(3, thirdArrival)));
        allowMatches();
        engine.executeTick();
        verify(groups)
                .create(
                        argThat(
                                c ->
                                        c.size() == expectedSize
                                                && c.requestIds().containsAll(List.of(1L, 2L))));
    }

    @Test
    void cancelledPartnerIsNotMatchedAtDeadline() {
        setTime(0);
        var first = request(1, -20);
        when(queue.findWaiting()).thenReturn(List.of(first, request(2, 0)));
        engine.executeTick();
        setTime(60);
        when(queue.findWaiting()).thenReturn(List.of(first));
        engine.checkPairDeadline();
        verifyNoInteractions(evaluator, groups);
    }
}
