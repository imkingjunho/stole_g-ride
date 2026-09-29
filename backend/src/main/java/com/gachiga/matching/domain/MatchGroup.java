package com.gachiga.matching.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "match_groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long hubId;
    private int totalFare;
    private int totalDistance;
    private int totalDuration;
    
    @Enumerated(EnumType.STRING)
    private GroupStatus status;
    
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime closedAt;
    private boolean estimated;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    private List<MatchMember> members = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = GroupStatus.PENDING;
    }

    public void addMember(MatchMember member) {
        members.add(member);
        member.setGroup(this);
    }

    public void confirm() {
        this.status = GroupStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = GroupStatus.COMPLETED;
        this.closedAt = LocalDateTime.now();
    }
}