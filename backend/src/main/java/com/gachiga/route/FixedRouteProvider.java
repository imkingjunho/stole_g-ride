package com.gachiga.route;

public class FixedRouteProvider {
    public RouteResult findRoute(Hub origin, Hub destination) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("출발지와 목적지는 모두 있어야 합니다.");
        }

        double meters = DistanceUtil.haversineMeters(
                origin.latitude(),
                origin.longitude(),
                destination.latitude(),
                destination.longitude()
        );

        int fare = (int) Math.round(Math.max(3500, meters * 0.18));
        long distance = Math.round(meters);
        long duration = Math.round(meters / 55.0 * 60.0);

        return new RouteResult(fare, distance, duration, true, "Fallback route estimate");
    }
}
