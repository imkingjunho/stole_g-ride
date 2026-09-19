package com.gachiga.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EstimatedRouteProvider} 검증.
 *
 * <p>가장 중요한 것은 <b>구간 거리의 합 == 전체 거리</b>다. 정산(PRD §5.2)이 구간 거리 비율로
 * 요금을 나누므로 이 둘이 어긋나면 분담액 합계가 총 요금과 맞지 않는다.
 */
class EstimatedRouteProviderTest {

    private final EstimatedRouteProvider provider = new EstimatedRouteProvider();

    private static final Coordinate BACK_GATE = new Coordinate(35.1760, 126.8977); // 전남대 후문
    private static final Coordinate SONGJEONG = new Coordinate(35.1372, 126.7913); // 광주송정역

    @Test
    @DisplayName("경유지가 없으면 구간은 1개다")
    void noWaypointMakesOneSection() {
        RouteResult result = provider.findRoute(BACK_GATE, List.of(), SONGJEONG);

        assertThat(result.sections()).hasSize(1);
        assertThat(result.estimated()).isTrue();
        assertThat(result.rawJson()).isNull();
    }

    @Test
    @DisplayName("경유지가 N개면 구간은 N+1개이고 순서가 유지된다")
    void waypointsMakeNPlusOneSections() {
        Coordinate first = new Coordinate(35.1700, 126.9100);
        Coordinate second = new Coordinate(35.1600, 126.9300);

        RouteResult result = provider.findRoute(BACK_GATE, List.of(first, second), SONGJEONG);

        assertThat(result.sections()).hasSize(3);
        // 각 구간의 path 는 [시작, 끝] 두 점이다
        assertThat(result.sections().get(0).path()).containsExactly(BACK_GATE, first);
        assertThat(result.sections().get(1).path()).containsExactly(first, second);
        assertThat(result.sections().get(2).path()).containsExactly(second, SONGJEONG);
    }

    @Test
    @DisplayName("구간 거리의 합은 전체 거리와 정확히 같다 (PRD §5.2 정산의 전제)")
    void sectionDistancesSumToTotal() {
        RouteResult result =
                provider.findRoute(
                        BACK_GATE,
                        List.of(new Coordinate(35.1700, 126.9100), new Coordinate(35.1600, 126.9300)),
                        SONGJEONG);

        int sum = result.sections().stream().mapToInt(RouteResult.Section::distance).sum();
        assertThat(sum).isEqualTo(result.totalDistance());

        int durationSum = result.sections().stream().mapToInt(RouteResult.Section::duration).sum();
        assertThat(durationSum).isEqualTo(result.totalDuration());
    }

    @Test
    @DisplayName("거리는 직선거리에 도로 보정 1.3배를 적용한 값이다")
    void distanceAppliesDetourFactor() {
        RouteResult result = provider.findRoute(BACK_GATE, List.of(), SONGJEONG);

        // 후문 → 송정역 직선 약 10,592m × 1.3 ≈ 13,769m
        assertThat(result.totalDistance()).isBetween(13_700, 13_840);
    }

    @Test
    @DisplayName("기본거리 1.7km 이내는 기본요금 4,800원이다")
    void withinBaseDistanceChargesBaseFare() {
        // 같은 지점 → 거리 0
        RouteResult result = provider.findRoute(BACK_GATE, List.of(), BACK_GATE);

        assertThat(result.totalDistance()).isZero();
        assertThat(result.totalFare()).isEqualTo(4_800);
    }

    @Test
    @DisplayName("기본거리를 넘으면 132m 마다 100원씩 오른다")
    void beyondBaseDistanceAddsStepFare() {
        // 후문 → 송정역 약 13,769m. (13769 - 1700) / 132 = 91.4 → 92단계 → 4,800 + 9,200
        RouteResult result = provider.findRoute(BACK_GATE, List.of(), SONGJEONG);

        assertThat(result.totalFare()).isEqualTo(4_800 + stepsFor(result.totalDistance()) * 100);
        assertThat(result.totalFare()).isBetween(13_900, 14_100);
    }

    @Test
    @DisplayName("요금은 100원 단위이고 기본요금보다 낮아지지 않는다")
    void fareIsAlwaysAtLeastBaseFareAndRoundedTo100() {
        List<Coordinate> destinations =
                List.of(
                        new Coordinate(35.1761, 126.8978), // 10m 남짓
                        new Coordinate(35.1850, 126.9100), // 2km 남짓
                        SONGJEONG);

        for (Coordinate destination : destinations) {
            RouteResult result = provider.findRoute(BACK_GATE, List.of(), destination);

            assertThat(result.totalFare()).isGreaterThanOrEqualTo(4_800);
            assertThat(result.totalFare() % 100).isZero();
        }
    }

    @Test
    @DisplayName("waypoints 가 null 이어도 터지지 않는다")
    void nullWaypointsIsTreatedAsEmpty() {
        RouteResult result = provider.findRoute(BACK_GATE, null, SONGJEONG);

        assertThat(result.sections()).hasSize(1);
    }

    /** 기본거리를 넘은 만큼이 132m 씩 몇 단계인지 (올림) */
    private int stepsFor(int totalDistance) {
        if (totalDistance <= 1_700) {
            return 0;
        }
        int extra = totalDistance - 1_700;
        return (extra + 131) / 132;
    }
}
