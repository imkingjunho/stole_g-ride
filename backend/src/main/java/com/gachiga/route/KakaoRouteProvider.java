package com.gachiga.route;

public class KakaoRouteProvider {
    private final String apiKey;

    public KakaoRouteProvider() {
        this.apiKey = null;
    }

    public KakaoRouteProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    public RouteResult findRoute(Hub origin, Hub destination) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("출발지와 목적지는 모두 있어야 합니다.");
        }

        if (apiKey == null || apiKey.isBlank()) {
            return new FixedRouteProvider().findRoute(origin, destination);
        }

        return new FixedRouteProvider().findRoute(origin, destination);
    }
}
