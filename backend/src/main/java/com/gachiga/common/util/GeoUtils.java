package com.gachiga.common.util;

/**
 * 좌표 계산 유틸. 모든 모듈이 함께 쓴다.
 *
 * <p>여기 있는 계산은 <b>직선거리</b>다. 실제 도로 거리가 필요하면
 * {@code contract.route.RouteProvider} 를 쓴다. 직선거리는
 * 카카오 API 없이도 되는 빠른 판정(최소 거리 검증 E-08, 목적지 근접 판정 E-07,
 * 경로 추정 fallback E-03)에만 쓴다.
 *
 * <p>좌표·거리는 {@code double} 을 쓴다. 금액에 {@code double} 을 쓰지 않는 것과 별개다(CLAUDE.md §5).
 */
public final class GeoUtils {

    /**
     * 지구 평균 반지름(m). IUGG 가 정한 평균 반지름 6,371,008.8m 를 쓴다.
     * 광주 시내 정도의 거리에서는 어떤 값을 쓰든 오차가 0.01% 미만이다.
     */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8d;

    /** 유틸 클래스이므로 인스턴스를 만들지 못하게 막는다 */
    private GeoUtils() {
        throw new AssertionError("유틸리티 클래스는 인스턴스를 만들지 않는다");
    }

    /**
     * 두 좌표 사이의 대권 거리(great-circle distance)를 미터로 돌려준다 — Haversine 공식.
     *
     * <p>지구를 완전한 구로 보고 계산하므로 실제와 최대 0.5% 차이가 난다. 도로를 따라가는 거리가
     * 아니라 <b>직선거리</b>이므로, 실제 이동 거리는 보통 이 값의 1.2~1.5배다.
     *
     * @param lat1 출발 위도(도). 예: 35.1760
     * @param lng1 출발 경도(도). 예: 126.8977
     * @param lat2 도착 위도(도)
     * @param lng2 도착 경도(도)
     * @return 두 지점 사이의 직선거리(m). 항상 0 이상이며 두 좌표가 같으면 0
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double latDeltaRad = Math.toRadians(lat2 - lat1);
        double lngDeltaRad = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        // a = 두 점 사이 각거리의 절반에 대한 sin² 값
        double a =
                Math.sin(latDeltaRad / 2) * Math.sin(latDeltaRad / 2)
                        + Math.cos(lat1Rad)
                                * Math.cos(lat2Rad)
                                * Math.sin(lngDeltaRad / 2)
                                * Math.sin(lngDeltaRad / 2);

        // 부동소수점 오차로 a 가 1 을 아주 조금 넘으면 sqrt(1 - a) 가 NaN 이 된다.
        // 지구 반대편에 가까운 좌표에서 실제로 일어나므로 1 로 잘라 막는다.
        double clamped = Math.min(1.0d, a);

        // atan2 를 쓰면 a 가 1 에 가까울 때 생기는 수치 오차를 더 줄일 수 있다
        double centralAngle = 2 * Math.atan2(Math.sqrt(clamped), Math.sqrt(1 - clamped));
        return EARTH_RADIUS_METERS * centralAngle;
    }
}
