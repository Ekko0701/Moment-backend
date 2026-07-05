package com.moment.adapter.out.persistence.report;

import com.moment.domain.safety.Report;
import com.moment.domain.safety.ReportReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEntity {

    @Id
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static ReportEntity fromDomain(Report report) {
        ReportEntity entity = new ReportEntity();
        entity.id = report.id();
        entity.applyDomain(report);
        entity.createdAt = report.createdAt();
        return entity;
    }

    void applyDomain(Report report) {
        this.reporterId = report.reporterId();
        this.momentId = report.momentId();
        this.reason = report.reason();
        this.detail = report.detail();
    }

    Report toDomain() {
        return new Report(id, reporterId, momentId, reason, detail, createdAt);
    }
}
