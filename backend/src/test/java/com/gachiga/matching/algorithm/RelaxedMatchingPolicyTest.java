package com.gachiga.matching.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 목적지 3km 규칙 완화 · 직선 우회 사전 추정 · 최소 절감 기준 (2026-11-20 정책) */
class RelaxedMatchingPolicyTest {

    static final Coordinate MAIN_GATE = new Coordinate(35.17822, 126.90793); // 전남대 정문
    static final Coordinate USQUARE = new Coordinate(35.16104, 126.88031);
    static final Coordinate SONGJEONG = new Coordinate(35.13768, 126.79148);
    static final Coordinate SANGMU = new Coordinate(35.1601, 126.8514);
    static final Coordinate CHEOMDAN = new Coordinate(35.2187, 126.8456);
    static final Coordinate CHUNGJANG = new Coordinate(35.1478, 126.9177);
    static final Coordinate GWANGJU_STN = new Coordinate(35.1650, 126.9090);
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 18, 0);

    private static WaitingRequest req(long id, Coordinate dest, String detour, int soloFare) {
        return req(id, dest, detour, soloFare, NOW);
    }

    private static WaitingRequest req(
            long id, Coordinate dest, String detour, int soloFare, LocalDateTime created) {
        return new WaitingRequest(
                id, id, 2L, dest, "d" + id, created, created.plusMinutes(10), created, false,
                new BigDecimal(detour), 5000, soloFare, 0);
    }

    @Test
    @DisplayName("같은 방향 장거리(유스퀘어 + 광주송정역, 목적지 간 8.5km)는 직선 우회 추정을 통과한다")
    void sameDirectionLongTripPassesPrecheck() {
        var combo = List.of(req(1, USQUARE, "0.20", 7400), req(2, SONGJEONG, "0.20", 14300));
        assertThat(CombinationEvaluator.straightLineWorstExcess(MAIN_GATE, combo)).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("가깝지만 방향이 어긋난 조합(광주역 + 유스퀘어, 2.6km · 추정 31%)은 한도 20%면 카카오 조회 전에 탈락")
    void nearButDivergingRejectedBeforeRouteCall() {
        var combo = List.of(req(1, GWANGJU_STN, "0.20", 5200), req(2, USQUARE, "0.20", 7400));
        assertThat(CombinationEvaluator.straightLineWorstExcess(MAIN_GATE, combo)).isGreaterThan(0);
    }

    @Test
    @DisplayName("반대 방향(첨단 + 충장로)은 한도 30%여도 탈락")
    void oppositeDirectionRejected() {
        var combo = List.of(req(1, CHUNGJANG, "0.30", 7100), req(2, CHEOMDAN, "0.30", 12000));
        assertThat(CombinationEvaluator.straightLineWorstExcess(MAIN_GATE, combo)).isGreaterThan(0);
    }

    @Test
    @DisplayName("최소 절감: 단독 1만 원 미만 30%, 기본요금도 1,500원 하한")
    void shortTripMinimumSaving() {
        assertThat(CombinationEvaluator.requiredSaving(req(1, USQUARE, "0.30", 7400), NOW)).isEqualTo(2220);
        assertThat(CombinationEvaluator.requiredSaving(req(1, GWANGJU_STN, "0.30", 4800), NOW)).isEqualTo(1500);
    }

    @Test
    @DisplayName("최소 절감: 장거리는 대기 전반 35%, 후반 20%")
    void longTripMinimumSavingRelaxesWithWait() {
        var early = req(1, SONGJEONG, "0.30", 14300, NOW.minusMinutes(2));
        var late = req(1, SONGJEONG, "0.30", 14300, NOW.minusMinutes(6));
        assertThat(CombinationEvaluator.requiredSaving(early, NOW)).isEqualTo(5005);
        assertThat(CombinationEvaluator.requiredSaving(late, NOW)).isEqualTo(2860);
    }

    @Test
    @DisplayName("클러스터: 같은 서쪽 축(유스퀘어·상무·송정역)은 한 버킷, 반대 방향(충장로)은 다른 버킷")
    void bearingBuckets() {
        var west = List.of(req(1, USQUARE, "0.3", 7400), req(2, SANGMU, "0.3", 9600), req(3, SONGJEONG, "0.3", 14300));
        var south = req(4, CHUNGJANG, "0.3", 7100);
        var all = new java.util.ArrayList<>(west);
        all.add(south);
        var clusters = Clustering.cluster(all, Map.of(2L, MAIN_GATE)::get);
        assertThat(clusters).anySatisfy(c -> assertThat(c).containsExactlyInAnyOrderElementsOf(west));
        assertThat(clusters).anySatisfy(c -> assertThat(c).containsExactly(south));
    }
}
