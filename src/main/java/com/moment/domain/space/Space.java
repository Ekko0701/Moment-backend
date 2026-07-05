package com.moment.domain.space;

import java.time.Instant;
import java.util.UUID;

/**
 * 스페이스 도메인 모델.
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record Space(
        UUID id,
        SpaceType type,
        String name,
        int maxMembers,
        SpaceStatus status,
        Instant createdAt) {

    /**
     * 1대1 스페이스 생성 (maxMembers=2, ACTIVE).
     */
    public static Space oneToOne(UUID id, Instant now) {
        return new Space(id, SpaceType.ONE_TO_ONE, null, 2, SpaceStatus.ACTIVE, now);
    }

    /**
     * 스페이스 비활성화.
     */
    public Space deactivate() {
        return new Space(id, type, name, maxMembers, SpaceStatus.INACTIVE, createdAt);
    }
}
