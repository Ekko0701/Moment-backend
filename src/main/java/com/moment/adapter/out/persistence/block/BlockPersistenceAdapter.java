package com.moment.adapter.out.persistence.block;

import com.moment.application.port.out.BlockPort;
import com.moment.domain.safety.Block;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BlockPersistenceAdapter implements BlockPort {

    private final BlockJpaRepository jpaRepository;

    BlockPersistenceAdapter(BlockJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Block save(Block block) {
        BlockEntity entity = jpaRepository.findByBlockerIdAndBlockedId(block.blockerId(), block.blockedId())
                .orElseGet(() -> BlockEntity.fromDomain(block));
        entity.applyDomain(block);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public boolean existsBlockBetween(UUID a, UUID b) {
        return jpaRepository.existsBlockBetween(a, b);
    }

    @Override
    public Set<UUID> findBlockedUserIdsEitherDirection(UUID userId) {
        Set<UUID> ids = new HashSet<>(jpaRepository.findBlockedIdsByBlocker(userId));
        ids.addAll(jpaRepository.findBlockerIdsByBlocked(userId));
        return ids;
    }
}
