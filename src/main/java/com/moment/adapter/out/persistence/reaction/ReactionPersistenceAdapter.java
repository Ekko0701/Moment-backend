package com.moment.adapter.out.persistence.reaction;

import com.moment.application.port.out.ReactionPort;
import com.moment.domain.moment.Reaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ReactionPersistenceAdapter implements ReactionPort {

    private final ReactionJpaRepository jpaRepository;

    ReactionPersistenceAdapter(ReactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Reaction save(Reaction reaction) {
        ReactionEntity entity = jpaRepository.findById(reaction.id())
                .map(existing -> {
                    existing.applyDomain(reaction);
                    return existing;
                })
                .orElseGet(() -> ReactionEntity.fromDomain(reaction));
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Reaction> findByMomentIdAndUserId(UUID momentId, UUID userId) {
        return jpaRepository.findByMomentIdAndUserId(momentId, userId)
                .map(ReactionEntity::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Reaction> findAllByMomentIdIn(List<UUID> momentIds) {
        return jpaRepository.findByMomentIdIn(momentIds).stream()
                .map(ReactionEntity::toDomain)
                .toList();
    }
}
