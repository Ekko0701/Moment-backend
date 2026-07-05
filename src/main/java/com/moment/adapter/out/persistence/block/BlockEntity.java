package com.moment.adapter.out.persistence.block;

import com.moment.domain.safety.Block;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockEntity {

    @Id
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static BlockEntity fromDomain(Block block) {
        BlockEntity entity = new BlockEntity();
        entity.id = block.id();
        entity.applyDomain(block);
        entity.createdAt = block.createdAt();
        return entity;
    }

    void applyDomain(Block block) {
        this.blockerId = block.blockerId();
        this.blockedId = block.blockedId();
    }

    Block toDomain() {
        return new Block(id, blockerId, blockedId, createdAt);
    }
}

