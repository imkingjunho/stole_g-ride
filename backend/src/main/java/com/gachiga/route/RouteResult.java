package com.gachiga.route;

public record RouteResult(
        int totalFare,
        long totalDistanceMeters,
        long totalDurationSeconds,
        boolean estimated,
        String summary
) {
}
