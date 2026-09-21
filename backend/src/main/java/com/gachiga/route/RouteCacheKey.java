package com.gachiga.route;

import java.util.Locale;

public final class RouteCacheKey {
    private static final int SCALE = 4;

    private RouteCacheKey() {
    }

    public static String of(double originLat, double originLon, double destinationLat, double destinationLon) {
        return String.format(
                Locale.ROOT,
                "route:v1:%s:%s:%s:%s",
                format(originLat),
                format(originLon),
                format(destinationLat),
                format(destinationLon)
        );
    }

    public static String of(Hub origin, Hub destination) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("출발지와 목적지는 모두 있어야 합니다.");
        }

        return of(origin.latitude(), origin.longitude(), destination.latitude(), destination.longitude());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", round(value));
    }

    private static double round(double value) {
        double scale = Math.pow(10, SCALE);
        return Math.round(value * scale) / scale;
    }
}
