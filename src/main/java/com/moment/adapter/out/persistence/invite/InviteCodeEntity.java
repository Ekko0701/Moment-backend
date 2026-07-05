package com.moment.adapter.out.persistence.invite;

import com.moment.domain.invite.InviteCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "invite_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCodeEntity {

    @Id
    @Column(length = 6)
    private String code;

    @Column(name = "issuer_user_id", nullable = false)
    private UUID issuerUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static InviteCodeEntity fromDomain(InviteCode inviteCode) {
        InviteCodeEntity entity = new InviteCodeEntity();
        entity.code = inviteCode.code();
        entity.applyDomain(inviteCode);
        return entity;
    }

    void applyDomain(InviteCode inviteCode) {
        this.issuerUserId = inviteCode.issuerUserId();
        this.expiresAt = inviteCode.expiresAt();
        this.usedAt = inviteCode.usedAt();
        this.createdAt = inviteCode.createdAt();
    }

    InviteCode toDomain() {
        return new InviteCode(code, issuerUserId, expiresAt, usedAt, createdAt);
    }
}
