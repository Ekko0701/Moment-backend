package com.moment.adapter.out.persistence.space;

import com.moment.domain.space.MemberRole;
import com.moment.domain.space.SpaceMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "space_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"space_id", "user_id"}),
        indexes = @Index(columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceMemberEntity {

    @Id
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    static SpaceMemberEntity fromDomain(SpaceMember member) {
        SpaceMemberEntity entity = new SpaceMemberEntity();
        entity.id = member.id();
        entity.applyDomain(member);
        return entity;
    }

    void applyDomain(SpaceMember member) {
        this.spaceId = member.spaceId();
        this.userId = member.userId();
        this.role = member.role();
        this.joinedAt = member.joinedAt();
        this.leftAt = member.leftAt();
    }

    SpaceMember toDomain() {
        return new SpaceMember(id, spaceId, userId, role, joinedAt, leftAt);
    }

    UUID getSpaceId() {
        return spaceId;
    }

    Instant getLeftAt() {
        return leftAt;
    }

    boolean isActive() {
        return leftAt == null;
    }
}
