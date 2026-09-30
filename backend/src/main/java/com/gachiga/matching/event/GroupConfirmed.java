package com.gachiga.matching.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class GroupConfirmed extends ApplicationEvent {
    private final Long groupId;
    private final Long hubId;
    private final List<Long> memberUserIds;
    private final List<Long> requestIds;
    private final Integer totalFare;
    private final LocalDateTime confirmedAt;

    public GroupConfirmed(Object source, Long groupId, Long hubId, 
                        List<Long> memberUserIds, List<Long> requestIds,
                        Integer totalFare, LocalDateTime confirmedAt) {
        super(source);
        this.groupId = groupId;
        this.hubId = hubId;
        this.memberUserIds = memberUserIds;
        this.requestIds = requestIds;
        this.totalFare = totalFare;
        this.confirmedAt = confirmedAt;
    }
}