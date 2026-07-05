package com.moment.adapter.out.persistence.block;

import com.moment.application.port.out.BlockPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BlockPersistenceAdapter implements BlockPort {

    private final BlockJpaRepository jpaRepository;

    BlockPersistenceAdapter(BlockJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsBlockBetween(UUID a, UUID b) {
        return jpaRepository.existsBlockBetween(a, b);
    }
}
