package com.moment.domain.safety;

import java.time.Instant;
import java.util.UUID;

/**
 * 신고 도메인 모델 (기능정의서 F-06).
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record Report(
        UUID id,
        UUID reporterId,
        UUID momentId,
        ReportReason reason,
        String detail,
        Instant createdAt) {

    /**
     * 새 신고 생성.
     */
    public static Report create(UUID id, UUID reporterId, UUID momentId, ReportReason reason,
            String detail, Instant now) {
        return new Report(id, reporterId, momentId, reason, detail, now);
    }
}
