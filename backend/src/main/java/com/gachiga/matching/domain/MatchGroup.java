package com.gachiga.matching.domain;

import com.gachiga.contract.route.RouteResult;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "match_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class MatchGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hubId;

    @Column(nullable = false)
    private Integer totalFare;

    @Column(nullable = false)
    private Integer totalDistance;

    @Column(nullable = false)
    private Integer totalDuration;

    @Column(columnDefinition = "LONGTEXT")
    private String routeJson;

    @Column(nullable = false)
    @Builder.Default
    private Boolean estimated = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GroupStatus status = GroupStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MatchMember> members = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void confirm() {
        this.status = GroupStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = GroupStatus.COMPLETED;
        this.closedAt = LocalDateTime.now();
    }

    public void dissolve() {
        this.status = GroupStatus.DISSOLVED;
        this.closedAt = LocalDateTime.now();
    }

    public void addMember(MatchMember member) {
        members.add(member);
        member.setGroup(this);
    }
}