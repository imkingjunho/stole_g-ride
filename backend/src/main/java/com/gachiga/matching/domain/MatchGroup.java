package com.gachiga.matching.domain;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_groups",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_pair_offer", columnNames = "pair_offer_key"),
        indexes = @Index(name = "idx_group_offer_expiry", columnList = "status,accept_deadline"))
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
    private LocalDateTime acceptDeadline;
    @Column(name = "pair_offer_key", length = 48)
    private String pairOfferKey;
    private boolean estimated;

    // 평가 당시의 허브·경로를 보관한다. 조회할 때 다시 계산하면 정산과 경로가 어긋난다.
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String routeJson;

    private String hubName;
    private Double hubLat;
    private Double hubLng;
    private String hubType;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = GroupStatus.PENDING;
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
