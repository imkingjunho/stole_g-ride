package com.gachiga.ride.dto;

import com.gachiga.contract.route.HubInfo;
import com.gachiga.ride.RideRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 내 요청의 현재 상태. {@code docs/api-spec.yaml} 의 {@code RideRequestDetail} 과 1:1 이다.
 *
 * <p>대기 화면이 이 값으로 카운트다운과 후보 수를 그린다 (FR-09).
 *
 * @param remainingSeconds 만료까지 남은 시간(초). 이미 지났으면 0
 * @param candidateCount   같은 거점에서 함께 기다리는 사람 수(나 자신 제외)
 * @param estimated        {@code soloFare} 가 카카오가 아닌 추정치면 true (E-03)
 * @param groupId          매칭된 뒤에만 값이 있다. 그룹 이벤트를 받을 때 적고, 해체돼 대기로 돌아오면
 *                         비운다. 탑승 완료 뒤에도 남는다
 */
public record RideRequestResponse(
        Long requestId,
        Long userId,
        HubResponse hub,
        String destName,
        double destLat,
        double destLng,
        LocalDateTime departAt,
        LocalDateTime expiresAt,
        int maxWaitMin,
        boolean sameGenderOnly,
        BigDecimal maxDetourRatio,
        int soloDistance,
        int soloFare,
        String status,
        int remainingSeconds,
        int candidateCount,
        boolean estimated,
        Long groupId,
        LocalDateTime createdAt) {

    /**
     * 엔티티를 응답으로 옮긴다.
     *
     * <p>{@code groupId} 는 그룹 이벤트({@code GroupProposed}·{@code GroupConfirmed})를 받을 때
     * 요청에 적어 둔 값이다. 매칭 전이나 그룹이 해체된 뒤에는 null 이다.
     */
    public static RideRequestResponse of(
            RideRequest request, HubInfo hub, int candidateCount, LocalDateTime now) {
        return new RideRequestResponse(
                request.getId(),
                request.getUserId(),
                HubResponse.from(hub),
                request.getDestName(),
                request.getDestLat(),
                request.getDestLng(),
                request.getDepartAt(),
                request.getExpiresAt(),
                request.getMaxWaitMin(),
                request.isSameGenderOnly(),
                request.getMaxDetourRatio(),
                request.getSoloDistance(),
                request.getSoloFare(),
                request.getStatus().name(),
                request.remainingSeconds(now),
                candidateCount,
                request.isEstimated(),
                request.getGroupId(),
                request.getCreatedAt());
    }
}
