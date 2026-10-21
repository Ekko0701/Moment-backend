package com.moment.adapter.out.persistence.space;

import com.moment.domain.space.GroupJoinRequest;
import com.moment.domain.space.JoinRequestStatus;
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
@Table(name = "group_join_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupJoinRequestEntity {

    @Id
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JoinRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    static GroupJoinRequestEntity fromDomain(GroupJoinRequest request) {
        GroupJoinRequestEntity entity = new GroupJoinRequestEntity();
        entity.id = request.id();
        entity.spaceId = request.spaceId();
        entity.userId = request.userId();
        entity.status = request.status();
        entity.createdAt = request.createdAt();
        entity.decidedAt = request.decidedAt();
        return entity;
    }

    GroupJoinRequest toDomain() {
        return new GroupJoinRequest(id, spaceId, userId, status, createdAt, decidedAt);
    }
}
