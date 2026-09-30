package com.gachiga.matching.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
public class GroupCompleted extends ApplicationEvent {
    private final Long groupId;
    private final List<Long> memberUserIds;
    private final Map<Long, Integer> shareAmountByUserId;
    private final Map<Long, Integer> savingAmountByUserId;
    private final Integer totalFare;
    private final LocalDateTime completedAt;

    public GroupCompleted(Object source, Long groupId, List<Long> memberUserIds,
                        Map<Long, Integer> shareAmountByUserId,
                        Map<Long, Integer> savingAmountByUserId,
                        Integer totalFare, LocalDateTime completedAt) {
        super(source);
        this.groupId = groupId;
        this.memberUserIds = memberUserIds;
        this.shareAmountByUserId = shareAmountByUserId;
        this.savingAmountByUserId = savingAmountByUserId;
        this.totalFare = totalFare;
        this.completedAt = completedAt;
    }
}