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
     * 그룹 스페이스 생성 (ACTIVE). 정원은 생성 시점의 정책값을 기록한다.
     */
    public static Space group(UUID id, String name, int maxMembers, Instant now) {
        return new Space(id, SpaceType.GROUP, name, maxMembers, SpaceStatus.ACTIVE, now);
    }

    /**
     * 스페이스 비활성화.
     */
    public Space deactivate() {
        return new Space(id, type, name, maxMembers, SpaceStatus.INACTIVE, createdAt);
    }

    public Space rename(String newName) {
        return new Space(id, type, newName, maxMembers, status, createdAt);
    }

    public boolean isGroup() {
        return type == SpaceType.GROUP;
    }

    public boolean isActive() {
        return status == SpaceStatus.ACTIVE;
    }
}
