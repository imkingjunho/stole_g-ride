package com.gachiga.matching.api;

import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.matching.domain.GroupStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class GroupResponse {
    private Long id;
    private Long hubId;
    private Integer totalFare;
    private Integer totalDistance;
    private Integer totalDuration;
    private GroupStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime closedAt;
    private List<MemberInfo> members;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MemberInfo {
        private Long userId;
        private Long requestId;
        private int boardingOrder;
        private Integer shareAmount;
        private Integer savingAmount;
    }

    public static GroupResponse from(MatchGroup group) {
        return GroupResponse.builder()
            .id(group.getId())
            .hubId(group.getHubId())
            .totalFare(group.getTotalFare())
            .totalDistance(group.getTotalDistance())
            .totalDuration(group.getTotalDuration())
            .status(group.getStatus())
            .createdAt(group.getCreatedAt())
            .confirmedAt(group.getConfirmedAt())
            .closedAt(group.getClosedAt())
            .members(group.getMembers().stream()
                .map(m -> MemberInfo.builder()
                    .userId(m.getUserId())
                    .requestId(m.getRequestId())
                    .boardingOrder(m.getBoardingOrder())
                    .shareAmount(m.getShareAmount())
                    .savingAmount(m.getSavingAmount())
                    .build())
                .toList())
            .build();
    }
}