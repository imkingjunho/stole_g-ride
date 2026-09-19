package com.gachiga.contract.route;

/**
 * 위경도 좌표 한 쌍. WGS84 (카카오맵과 같은 좌표계)
 *
 * @param lat 위도(도). 광주는 대략 35.1 ~ 35.2
 * @param lng 경도(도). 광주는 대략 126.7 ~ 127.0
 */
public record Coordinate(double lat, double lng) {}
