package com.moment.adapter.out.persistence.space;

import com.moment.domain.space.GroupInviteCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "group_invite_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupInviteCodeEntity {

    @Id
    @Column(length = 6)
    private String code;

    @Column(name = "space_id", nullable = false, unique = true)
    private UUID spaceId;

    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static GroupInviteCodeEntity fromDomain(GroupInviteCode code) {
        GroupInviteCodeEntity entity = new GroupInviteCodeEntity();
        entity.code = code.code();
        entity.spaceId = code.spaceId();
        entity.issuedBy = code.issuedBy();
        entity.expiresAt = code.expiresAt();
        entity.createdAt = code.createdAt();
        return entity;
    }

    GroupInviteCode toDomain() {
        return new GroupInviteCode(code, spaceId, issuedBy, expiresAt, createdAt);
    }
}
