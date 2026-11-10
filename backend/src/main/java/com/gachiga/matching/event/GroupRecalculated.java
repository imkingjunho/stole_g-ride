package com.gachiga.matching.event;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
public class GroupRecalculated extends ApplicationEvent {
    private final Long groupId;
    private final List<Long> remainingMemberUserIds;
    private final List<Long> removedMemberUserIds;
    private final Integer newTotalFare;
    private final Integer newTotalDistance;
    private final Map<Long, Integer> newShareByUserId;
    private final LocalDateTime recalculatedAt;

    public GroupRecalculated(Object source, Long groupId, 
                           List<Long> remainingMemberUserIds,
                           List<Long> removedMemberUserIds,
                           Integer newTotalFare, Integer newTotalDistance,
                           Map<Long, Integer> newShareByUserId,
                           LocalDateTime recalculatedAt) {
        super(source);
        this.groupId = groupId;
        this.remainingMemberUserIds = remainingMemberUserIds;
        this.removedMemberUserIds = removedMemberUserIds;
        this.newTotalFare = newTotalFare;
        this.newTotalDistance = newTotalDistance;
        this.newShareByUserId = newShareByUserId;
        this.recalculatedAt = recalculatedAt;
    }
}