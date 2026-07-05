package com.moment.domain.safety;

import java.time.Instant;
import java.util.UUID;

/**
 * 차단 도메인 모델 (기능정의서 F-07).
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record Block(
        UUID id,
        UUID blockerId,
        UUID blockedId,
        Instant createdAt) {

    /**
     * 새 차단 생성.
     */
    public static Block create(UUID id, UUID blockerId, UUID blockedId, Instant now) {
        return new Block(id, blockerId, blockedId, now);
    }
}
