package com.moment.adapter.out.persistence.reaction;

import com.moment.domain.moment.Reaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReactionEntity {

    @Id
    private UUID id;

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static ReactionEntity fromDomain(Reaction reaction) {
        ReactionEntity entity = new ReactionEntity();
        entity.id = reaction.id();
        entity.applyDomain(reaction);
        entity.createdAt = reaction.createdAt();
        return entity;
    }

    void applyDomain(Reaction reaction) {
        this.momentId = reaction.momentId();
        this.userId = reaction.userId();
        this.emoji = reaction.emoji();
    }

    Reaction toDomain() {
        return new Reaction(id, momentId, userId, emoji, createdAt);
    }
}
