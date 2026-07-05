package com.moment.adapter.out.persistence.moment;

import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.MomentRepositoryPort.TimelinePage;
import com.moment.domain.moment.Moment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class MomentPersistenceAdapter implements MomentRepositoryPort {

    private final MomentJpaRepository jpaRepository;

    MomentPersistenceAdapter(MomentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Moment save(Moment moment) {
        MomentEntity entity = jpaRepository.findById(moment.id())
                .map(existing -> {
                    existing.applyDomain(moment);
                    return existing;
                })
                .orElseGet(() -> MomentEntity.fromDomain(moment));
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Moment> findById(UUID id) {
        return jpaRepository.findById(id).map(MomentEntity::toDomain);
    }

    @Override
    public TimelinePage findTimeline(UUID spaceId, Instant cursor, int limit) {
        List<Moment> moments = jpaRepository.findTimeline(spaceId, cursor, limit)
                .stream()
                .map(MomentEntity::toDomain)
                .toList();
        return new TimelinePage(moments, null, false);
    }

    @Override
    public Optional<Moment> findLatestBySpace(UUID spaceId, UUID excludeAuthorId) {
        return jpaRepository.findLatestBySpace(spaceId, excludeAuthorId)
                .map(MomentEntity::toDomain);
    }
}
