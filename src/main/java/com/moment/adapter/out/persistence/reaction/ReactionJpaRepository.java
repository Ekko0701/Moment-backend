package com.moment.adapter.out.persistence.reaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReactionJpaRepository extends JpaRepository<ReactionEntity, UUID> {

    Optional<ReactionEntity> findByMomentIdAndUserId(UUID momentId, UUID userId);

    List<ReactionEntity> findByMomentIdIn(List<UUID> momentIds);
}
