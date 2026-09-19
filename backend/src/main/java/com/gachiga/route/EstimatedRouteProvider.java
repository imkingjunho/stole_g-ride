package com.gachiga.route;

import com.gachiga.common.util.GeoUtils;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteProvider;
import com.gachiga.contract.route.RouteResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 카카오 없이 직선거리로 경로와 요금을 <b>추정</b>한다.
 *
 * <p>Phase 0 에는 이것이 유일한 {@link RouteProvider} 라 모든 경로가 추정치다. Phase 1 에
 * {@code KakaoRouteProvider} 가 {@code @Primary} 로 앞에 서고, <b>이 클래스는 지워지지 않고
 * fallback 으로 남는다</b> (E-03, CLAUDE.md §3). 카카오가 3초 안에 답하지 않거나 오류를 내면
 * 여기로 떨어진다.
 *
 * <p>결과에는 언제나 {@code estimated=true} 가 붙으므로 화면에 "추정치" 배지를 띄울 수 있다.
 *
 * <p><b>담당: 송준호 · Phase 1</b> (Phase 0 뼈대는 이승민이 깐다)
 */
@Component
public class EstimatedRouteProvider implements RouteProvider {

    /**
     * 직선거리 → 실제 도로 거리 보정 계수. 도로는 곧게 나 있지 않으므로 직선거리보다 길다 (E-03).
     */
    private static final double DETOUR_FACTOR = 1.3d;

    /**
     * 시내 평균 주행 속도(m/s). 25km/h 로 잡았다. 소요 시간 추정에만 쓰고 요금에는 반영하지 않는다.
     */
    private static final double AVERAGE_SPEED_MPS = 25_000d / 3_600d;

    // ── 광주 중형택시 요금표 ──────────────────────────────────────
    // 2025-10-22 00:00 시행분. 종전(4,300원/2km·134m)에서 인상되었다.
    // 근거: 2025-10-12~13 뉴시스·머니투데이·헤럴드경제 등 보도. 시청 고시 원문은 확인하지 못했다.
    // 요금표가 바뀌면 이 세 상수만 고치면 된다.

    /** 기본요금(원) */
    private static final int BASE_FARE = 4_800;

    /** 기본요금에 포함된 거리(m) */
    private static final int BASE_DISTANCE_METERS = 1_700;

    /** 기본거리를 넘은 뒤 요금이 한 번 오르는 거리(m) */
    private static final int FARE_STEP_METERS = 132;

    /** 한 단계당 오르는 금액(원) */
    private static final int FARE_STEP_WON = 100;

    /**
     * 출발지 → 경유지(순서대로) → 목적지 경로를 직선거리로 추정한다.
     *
     * <p>구간 거리의 합이 곧 전체 거리다. 정산(PRD §5.2)이 구간 거리 비율로 요금을 나누므로
     * 이 합이 어긋나면 안 된다.
     */
    @Override
    public RouteResult findRoute(
            Coordinate origin, List<Coordinate> waypoints, Coordinate destination) {

        // 출발지 → 경유지들 → 목적지를 한 줄로 세운다
        List<Coordinate> stops = new ArrayList<>();
        stops.add(origin);
        if (waypoints != null) {
            stops.addAll(waypoints);
        }
        stops.add(destination);

        List<RouteResult.Section> sections = new ArrayList<>();
        int totalDistance = 0;
        int totalDuration = 0;

        for (int i = 0; i < stops.size() - 1; i++) {
            Coordinate from = stops.get(i);
            Coordinate to = stops.get(i + 1);

            double straight = GeoUtils.haversineMeters(from.lat(), from.lng(), to.lat(), to.lng());
            int distance = (int) Math.round(straight * DETOUR_FACTOR);
            int duration = (int) Math.round(distance / AVERAGE_SPEED_MPS);

            // 추정치라 실제 도로 모양을 모른다. 지도에는 두 점을 잇는 직선만 그려진다
            sections.add(new RouteResult.Section(distance, duration, List.of(from, to)));
            totalDistance += distance;
            totalDuration += duration;
        }

        return new RouteResult(
                estimateFare(totalDistance), totalDistance, totalDuration, sections, true, null);
    }

    /**
     * 거리만으로 택시 요금을 추정한다.
     *
     * <p>시간 병산(시속 15km 이하일 때 32초당 100원)과 심야·시계외 할증은 넣지 않는다.
     * {@link RouteProvider#findRoute} 에 출발 시각이 없어 심야 여부를 알 수 없고, 정체 정도도
     * 알 수 없기 때문이다. 그만큼 <b>실제보다 낮게</b> 나올 수 있다.
     *
     * @param distanceMeters 전체 이동 거리(m)
     * @return 예상 요금(원). 최소 기본요금
     */
    private int estimateFare(int distanceMeters) {
        if (distanceMeters <= BASE_DISTANCE_METERS) {
            return BASE_FARE;
        }
        int extraMeters = distanceMeters - BASE_DISTANCE_METERS;
        // 한 걸음이라도 넘어가면 100원이 오른다 — 올림
        int steps = (extraMeters + FARE_STEP_METERS - 1) / FARE_STEP_METERS;
        return BASE_FARE + steps * FARE_STEP_WON;
    }
}
