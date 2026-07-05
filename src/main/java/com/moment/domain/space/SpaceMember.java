package com.moment.domain.space;

import java.time.Instant;
import java.util.UUID;

/**
 * 스페이스 멤버 도메인 모델.
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record SpaceMember(
        UUID id,
        UUID spaceId,
        UUID userId,
        MemberRole role,
        Instant joinedAt,
        Instant leftAt) {

    /**
     * 멤버가 스페이스를 떠남.
     */
    public SpaceMember leave(Instant now) {
        return new SpaceMember(id, spaceId, userId, role, joinedAt, now);
    }

    /**
     * 멤버가 활성 상태인지 확인 (leftAt == null).
     */
    public boolean isActive() {
        return leftAt == null;
    }
}
