package com.gachiga.contract.route;

import java.util.List;

/**
 * 길찾기. 구현은 {@code route/} (송준호), 사용은 서준(조합 평가)과 이승민(단독 요금 캐시)이다.
 *
 * <p>Phase 0 에는 {@code route/EstimatedRouteProvider} 가 직선거리 추정으로 답한다. Phase 1 에
 * 카카오 실호출 구현이 {@code @Primary} 로 앞에 서고, 이 추정 구현은 fallback 으로 남는다.
 */
public interface RouteProvider {

    /**
     * 출발지에서 경유지를 순서대로 들른 뒤 목적지까지 가는 경로를 구한다.
     *
     * <p><b>실패해도 예외를 던지지 않는다.</b> 카카오 호출이 안 되면 추정치에
     * {@code estimated=true} 를 달아 돌려준다 (E-03).
     *
     * @param origin      출발 거점 좌표. null 아님
     * @param waypoints   경유지(중간 하차 지점) 좌표를 <b>하차 순서대로</b>. 없으면 빈 리스트. null 아님
     * @param destination 최종 목적지 좌표. null 아님
     * @return 경로 결과. null 아님. {@code sections} 크기는 {@code waypoints.size() + 1}
     */
    RouteResult findRoute(Coordinate origin, List<Coordinate> waypoints, Coordinate destination);
}
