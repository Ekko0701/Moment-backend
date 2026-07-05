package com.moment.adapter.out.persistence.invite;

import com.moment.domain.invite.InvitationStatus;
import com.moment.domain.invite.InvitationVia;
import com.moment.domain.invite.SpaceInvitation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "space_invitations",
        indexes = {
            @Index(columnList = "to_user_id, status"),
            @Index(columnList = "from_user_id, status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceInvitationEntity {

    @Id
    private UUID id;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvitationVia via;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    static SpaceInvitationEntity fromDomain(SpaceInvitation invitation) {
        SpaceInvitationEntity entity = new SpaceInvitationEntity();
        entity.id = invitation.id();
        entity.applyDomain(invitation);
        return entity;
    }

    void applyDomain(SpaceInvitation invitation) {
        this.fromUserId = invitation.fromUserId();
        this.toUserId = invitation.toUserId();
        this.via = invitation.via();
        this.status = invitation.status();
        this.createdAt = invitation.createdAt();
        this.respondedAt = invitation.respondedAt();
    }

    SpaceInvitation toDomain() {
        return new SpaceInvitation(id, fromUserId, toUserId, via, status, createdAt, respondedAt);
    }

    UUID getFromUserId() {
        return fromUserId;
    }

    UUID getToUserId() {
        return toUserId;
    }
}
