package com.moment.domain.space;

import java.time.Instant;
import java.util.UUID;

/**
 * 스페이스 멤버 도메인 모델.
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 *
 * @param notificationsEnabled 이 스페이스의 새 모먼트 푸시를 받을지 여부
 */
public record SpaceMember(
        UUID id,
        UUID spaceId,
        UUID userId,
        MemberRole role,
        Instant joinedAt,
        Instant leftAt,
        boolean notificationsEnabled) {

    /** 알림이 켜진 상태로 멤버를 만든다. */
    public SpaceMember(UUID id, UUID spaceId, UUID userId, MemberRole role, Instant joinedAt, Instant leftAt) {
        this(id, spaceId, userId, role, joinedAt, leftAt, true);
    }

    /**
     * 멤버가 스페이스를 떠남.
     */
    public SpaceMember leave(Instant now) {
        return new SpaceMember(id, spaceId, userId, role, joinedAt, now, notificationsEnabled);
    }

    /**
     * 떠났던 멤버를 일반 멤버로 다시 활성화한다. 가입 시각은 재입장 시각으로 갱신된다.
     */
    public SpaceMember rejoin(Instant now) {
        return new SpaceMember(id, spaceId, userId, MemberRole.MEMBER, now, null, true);
    }

    /** 방장 역할을 부여한다. */
    public SpaceMember promoteToOwner() {
        return new SpaceMember(id, spaceId, userId, MemberRole.OWNER, joinedAt, leftAt, notificationsEnabled);
    }

    public SpaceMember withNotifications(boolean enabled) {
        return new SpaceMember(id, spaceId, userId, role, joinedAt, leftAt, enabled);
    }

    /**
     * 멤버가 활성 상태인지 확인 (leftAt == null).
     */
    public boolean isActive() {
        return leftAt == null;
    }

    public boolean isOwner() {
        return role == MemberRole.OWNER;
    }
}
