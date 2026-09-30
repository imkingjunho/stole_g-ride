package com.gachiga.matching.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class GroupDissolved extends ApplicationEvent {
    private final Long groupId;
    private final List<Long> memberUserIds;
    private final String reason;
    private final LocalDateTime dissolvedAt;

    public GroupDissolved(Object source, Long groupId, List<Long> memberUserIds, 
                        String reason, LocalDateTime dissolvedAt) {
        super(source);
        this.groupId = groupId;
        this.memberUserIds = memberUserIds;
        this.reason = reason;
        this.dissolvedAt = dissolvedAt;
    }
}