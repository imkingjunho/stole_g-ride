package com.gachiga.matching.domain;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "match_members",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"group_id", "user_id"})})
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MatchMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer boardingOrder;

    @Column(nullable = false)
    private Integer dropoffOrder;

    @Builder.Default
    @Column(nullable = false)
    private Integer sharedDistance = 0;

    @Builder.Default
    @Column(precision = 5, scale = 3)
    private BigDecimal detourRatio = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private Integer shareAmount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer savingAmount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean accepted = false;

    private String destName;
    private Double destLat;
    private Double destLng;
    private Integer soloDistance;
    private Integer soloFare;
    @Builder.Default private Boolean completed = false;

    public void complete() {
        this.completed = true;
    }
}
