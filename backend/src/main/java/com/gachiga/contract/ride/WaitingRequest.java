package com.gachiga.contract.ride;

import com.gachiga.contract.route.Coordinate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 대기열에 있는 매칭 요청 하나. 매칭 엔진이 tick 마다 이 목록을 받아 조합을 만든다 (PRD §5.1).
 *
 * <p>{@code ride} 모듈의 엔티티를 그대로 넘기지 않고 이 레코드로 옮겨 담는다. 그래야 엔티티 구조가
 * 바뀌어도 매칭 쪽이 깨지지 않는다 (PRD §9.3 원칙 1).
 *
 * @param requestId      요청 id. null 아님
 * @param userId         요청한 사용자 id. null 아님
 * @param hubId          출발 거점 id. 매칭은 같은 거점끼리만 이뤄진다. null 아님
 * @param destination    목적지 좌표. null 아님
 * @param destName       목적지 표시 이름. null 아님
 * @param departAt       희망 출발 시각. 대기열 정렬 기준이다. null 아님
 * @param expiresAt      이 시각이 지나면 EXPIRED 가 된다. null 아님
 * @param createdAt      요청 생성 시각. null 아님
 * @param sameGenderOnly true 면 동성끼리만 매칭한다. 그룹에 한 명이라도 true 면 전원 동성이어야 한다 (E-06)
 * @param maxDetourRatio 허용하는 최대 우회 비율. 예: {@code 0.30} = 30%. null 아님
 * @param soloDistance   혼자 갈 때의 거리(m). 요청 생성 시점에 계산해 둔 값
 * @param soloFare       혼자 갈 때의 요금(원). 절감액 계산의 기준선이다
 * @param version        낙관적 락 버전. {@link RideRequestPort#tryMarkMatched} 에 그대로 넘긴다 (E-02)
 */
public record WaitingRequest(
        Long requestId,
        Long userId,
        Long hubId,
        Coordinate destination,
        String destName,
        LocalDateTime departAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        boolean sameGenderOnly,
        BigDecimal maxDetourRatio,
        int soloDistance,
        int soloFare,
        int version) {}
