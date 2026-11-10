package com.gachiga.matching.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class GroupProposed extends ApplicationEvent {
    private final Long groupId;
    private final Long hubId;
    private final List<Long> memberUserIds;
    private final Integer totalFare;
    private final LocalDateTime acceptDeadline;

    public GroupProposed(Object source, Long groupId, Long hubId, 
                        List<Long> memberUserIds, Integer totalFare, LocalDateTime acceptDeadline) {
        super(source);
        this.groupId = groupId;
        this.hubId = hubId;
        this.memberUserIds = memberUserIds;
        this.totalFare = totalFare;
        this.acceptDeadline = acceptDeadline;
    }
}