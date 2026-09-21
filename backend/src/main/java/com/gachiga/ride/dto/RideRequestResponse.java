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
 * @param groupId          매칭된 뒤에만 값이 있다. <b>현재는 항상 null</b> — 아래 주석 참고
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
     * <p><b>{@code groupId} 는 항상 null 이다.</b> 그룹은 {@code matching} 모듈 소유이고,
     * "이 요청이 어느 그룹에 들어갔는지" 를 물어볼 port 가 계약에 아직 없다.
     * 대기 화면이 매칭 후 그룹 화면으로 넘어가려면 필요하므로 서준에게 요청해 둔 상태다.
     * 그때까지 프론트는 WebSocket {@code /user/queue/match} 알림의 groupId 를 쓴다.
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
                null,
                request.getCreatedAt());
    }
}
