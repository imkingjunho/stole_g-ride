package com.gachiga.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 한 건 (PRD §7.1 {@code reports}, T3-1, FR-04).
 *
 * <p>{@code reportedId}·{@code groupId} 는 다른 모듈의 엔티티가 아니라 값(ID)만 저장한다 (PRD §7.2).
 * 그룹 구성원 여부 확인은 {@code contract.matching.MatchHistoryPort} 로 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_id", nullable = false)
    private Long reportedId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Report create(
            Long reporterId,
            Long reportedId,
            Long groupId,
            ReportReason reason,
            String detail,
            LocalDateTime now) {

        Report report = new Report();
        report.reporterId = reporterId;
        report.reportedId = reportedId;
        report.groupId = groupId;
        report.reason = reason;
        report.detail = detail;
        report.createdAt = now;
        return report;
    }
}
