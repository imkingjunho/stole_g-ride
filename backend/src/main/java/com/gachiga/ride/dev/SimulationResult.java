package com.gachiga.ride.dev;

import java.util.List;
import java.util.Map;

/**
 * 시뮬레이터 실행 결과.
 *
 * @param requested        요청한 건수
 * @param created          실제로 들어간 요청
 * @param skipped          건너뛴 사용자 수를 이유(에러 코드 이름)별로 센 것. 예: {@code {"NOT_FOUND": 3}}
 * @param scannedFromUserId 시도한 첫 사용자 id
 * @param scannedToUserId   시도한 마지막 사용자 id
 * @param note             요청한 만큼 못 넣었을 때의 안내. 다 넣었으면 null
 */
public record SimulationResult(
        int requested,
        List<Created> created,
        Map<String, Integer> skipped,
        long scannedFromUserId,
        long scannedToUserId,
        String note) {

    /**
     * 들어간 요청 하나.
     *
     * @param userId         요청한 사용자 id
     * @param requestId      만들어진 요청 id
     * @param destName       가상 목적지 이름. 예: {@code "가상 목적지 3 (북동 3.4km)"}
     * @param distanceMeters 거점에서 직선거리(m)
     */
    public record Created(Long userId, Long requestId, String destName, int distanceMeters) {}
}
