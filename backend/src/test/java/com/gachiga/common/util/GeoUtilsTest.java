package com.gachiga.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link GeoUtils} 직선거리 계산 검증. 외부 의존이 없는 순수 로직이므로 스프링 없이 돈다. */
class GeoUtilsTest {

    // 광주 실제 좌표 (PRD §14.4 예시 기준)
    private static final double BACK_GATE_LAT = 35.1760; // 전남대 후문
    private static final double BACK_GATE_LNG = 126.8977;

    @Test
    @DisplayName("같은 좌표면 거리는 0이다")
    void sameCoordinateIsZero() {
        double meters = GeoUtils.haversineMeters(
                BACK_GATE_LAT, BACK_GATE_LNG, BACK_GATE_LAT, BACK_GATE_LNG);

        assertThat(meters).isZero();
    }

    @Test
    @DisplayName("거리는 방향과 무관하다 (A→B 와 B→A 가 같다)")
    void isSymmetric() {
        double forward = GeoUtils.haversineMeters(BACK_GATE_LAT, BACK_GATE_LNG, 35.1372, 126.7913);
        double backward = GeoUtils.haversineMeters(35.1372, 126.7913, BACK_GATE_LAT, BACK_GATE_LNG);

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    @DisplayName("위도 1도는 약 111.2km 다")
    void oneDegreeOfLatitude() {
        double meters = GeoUtils.haversineMeters(0.0d, 0.0d, 1.0d, 0.0d);

        assertThat(meters).isCloseTo(111_195.0d, within(1.0d));
    }

    @Test
    @DisplayName("적도에서 경도 1도도 약 111.2km 다")
    void oneDegreeOfLongitudeOnEquator() {
        double meters = GeoUtils.haversineMeters(0.0d, 0.0d, 0.0d, 1.0d);

        assertThat(meters).isCloseTo(111_195.0d, within(1.0d));
    }

    @Test
    @DisplayName("전남대 후문에서 광주송정역까지 직선거리는 약 10.6km 다")
    void realWorldDistanceInGwangju() {
        double meters = GeoUtils.haversineMeters(BACK_GATE_LAT, BACK_GATE_LNG, 35.1372, 126.7913);

        assertThat(meters).isCloseTo(10_591.0d, within(50.0d));
    }

    @Test
    @DisplayName("최소 거리 500m 판정(E-08)에 쓸 수 있을 만큼 가까운 거리도 정확하다")
    void shortDistanceIsAccurate() {
        // 위도만 0.0009도(약 100m) 떨어뜨린 지점
        double meters = GeoUtils.haversineMeters(
                BACK_GATE_LAT, BACK_GATE_LNG, BACK_GATE_LAT + 0.0009d, BACK_GATE_LNG);

        assertThat(meters).isCloseTo(100.0d, within(1.0d));
        assertThat(meters).isLessThan(500.0d); // REQUEST_TOO_SHORT 로 걸러져야 하는 거리
    }

    @Test
    @DisplayName("지구 반대편 좌표에서도 수치 오차 없이 계산된다")
    void antipodalPointsDoNotOverflow() {
        double meters = GeoUtils.haversineMeters(0.0d, 0.0d, 0.0d, 180.0d);

        // 지구 둘레의 절반 = π × 평균 반지름
        assertThat(meters).isCloseTo(20_015_114.0d, within(10.0d));
    }
}
