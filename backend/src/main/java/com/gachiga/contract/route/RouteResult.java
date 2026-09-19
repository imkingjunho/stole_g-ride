package com.gachiga.contract.route;

import java.util.List;

/**
 * 길찾기 결과 (PRD §6.2).
 *
 * <p><b>이 결과는 실패하지 않는다.</b> 카카오 호출이 실패하면 구현체가 직선거리 기반 추정치를
 * 대신 채우고 {@code estimated=true} 로 표시한다 (E-03). 그러므로 호출하는 쪽은 예외 처리 대신
 * {@code estimated} 값만 확인해 화면에 "추정치" 배지를 띄우면 된다.
 *
 * @param totalFare     전체 예상 택시 요금(원). 카카오 {@code summary.fare.taxi}. 0 이상
 * @param totalDistance 전체 이동 거리(m). 0 이상
 * @param totalDuration 전체 소요 시간(초). 0 이상
 * @param sections      구간 목록. 경유지가 N개면 N+1개이며 <b>출발지에서부터 순서대로</b>다. null 아님
 * @param estimated     true 면 카카오 응답이 아니라 직선거리 기반 추정치다
 * @param rawJson       카카오 응답 원본. 추정치이거나 보관하지 않을 때는 null
 */
public record RouteResult(
        int totalFare,
        int totalDistance,
        int totalDuration,
        List<Section> sections,
        boolean estimated,
        String rawJson) {

    /**
     * 한 정차 지점에서 다음 정차 지점까지의 구간. 정산에서 "누가 어디까지 탔는지"를 나누는 단위다 (PRD §5.2).
     *
     * @param distance 이 구간의 거리(m)
     * @param duration 이 구간의 소요 시간(초)
     * @param path     지도에 그릴 좌표 목록. 추정치일 때는 시작·끝 두 점만 들어갈 수 있다. null 아님
     */
    public record Section(int distance, int duration, List<Coordinate> path) {}
}
