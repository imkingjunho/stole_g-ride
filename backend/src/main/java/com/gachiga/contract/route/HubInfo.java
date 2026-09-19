package com.gachiga.contract.route;

/**
 * 출발 거점 하나. 매칭은 <b>같은 거점에서 출발하는 요청끼리만</b> 이뤄진다 (PRD §5.1 Step 1).
 *
 * @param id   거점 id. null 아님
 * @param name 표시 이름. 예: {@code "전남대 후문"}. null 아님
 * @param lat  위도(도)
 * @param lng  경도(도)
 * @param type 거점 종류. {@code "CAMPUS"} · {@code "STATION"} · {@code "TERMINAL"} · {@code "SCHOOL"}
 *             중 하나다 (PRD §7.1 {@code hubs.type}). 비교할 때는 {@code equals} 를 쓴다. null 아님
 */
public record HubInfo(Long id, String name, double lat, double lng, String type) {}
