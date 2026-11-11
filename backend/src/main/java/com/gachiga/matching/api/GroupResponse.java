package com.gachiga.matching.api;

import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.RouteResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** API 명세의 GroupDetail. 금액과 경로는 저장된 평가 결과를 그대로 반환한다. */
public record GroupResponse(
        Long groupId,
        String status,
        boolean estimated,
        HubInfo hub,
        int totalFare,
        int totalDistance,
        int totalDuration,
        Route route,
        List<MemberInfo> members,
        LocalDateTime acceptDeadline,
        LocalDateTime createdAt,
        String fareMethod) {
    public record Route(List<RouteResult.Section> sections) {}

    public record MemberInfo(
            Long userId,
            String nickname,
            boolean isMe,
            int boardingOrder,
            int dropoffOrder,
            String destName,
            double destLat,
            double destLng,
            int soloDistance,
            int shareAmount,
            int soloFare,
            int savingAmount,
            BigDecimal detourRatio,
            boolean accepted) {}
}
