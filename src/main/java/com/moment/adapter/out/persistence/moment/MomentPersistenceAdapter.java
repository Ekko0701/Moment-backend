package com.moment.adapter.out.persistence.moment;

import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.MomentRepositoryPort.TimelinePage;
import com.moment.domain.moment.Moment;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    public TimelinePage findTimeline(UUID spaceId, Instant cursorCreatedAt, UUID cursorId, int limit) {
        // 커서 유무를 여기서 분기한다 — 쿼리에 null 파라미터를 넘기면 PostgreSQL이 거부한다
        List<MomentEntity> entities = (cursorCreatedAt == null)
                ? jpaRepository.findTimelineFirstPage(spaceId, limit)
                : jpaRepository.findTimelineAfterCursor(spaceId, cursorCreatedAt, cursorId, limit);
        List<Moment> moments = entities.stream()
                .map(MomentEntity::toDomain)
                .toList();
        return new TimelinePage(moments, null, false);
    }

    @Override
    public Optional<Moment> findLatestBySpace(UUID spaceId, UUID excludeAuthorId) {
        Optional<MomentEntity> entity = (excludeAuthorId == null)
                ? jpaRepository.findLatestBySpace(spaceId)
                : jpaRepository.findLatestBySpaceExcludingAuthor(spaceId, excludeAuthorId);
        return entity.map(MomentEntity::toDomain);
    }

    @Override
    public TimelinePage findTimeline(UUID spaceId, Instant cursorCreatedAt, UUID cursorId, int limit,
            Collection<UUID> excludedAuthorIds) {
        if (excludedAuthorIds.isEmpty()) {
            return findTimeline(spaceId, cursorCreatedAt, cursorId, limit);
        }
        List<MomentEntity> entities = (cursorCreatedAt == null)
                ? jpaRepository.findTimelineFirstPageExcluding(spaceId, excludedAuthorIds, limit)
                : jpaRepository.findTimelineAfterCursorExcluding(spaceId, excludedAuthorIds,
                        cursorCreatedAt, cursorId, limit);
        return new TimelinePage(entities.stream().map(MomentEntity::toDomain).toList(), null, false);
    }

    @Override
    public Optional<Moment> findLatestBySpaceExcluding(UUID spaceId, Collection<UUID> excludedAuthorIds) {
        Optional<MomentEntity> entity = excludedAuthorIds.isEmpty()
                ? jpaRepository.findLatestBySpace(spaceId)
                : jpaRepository.findLatestBySpaceExcludingAuthors(spaceId, excludedAuthorIds);
        return entity.map(MomentEntity::toDomain);
    }

    @Override
    public List<Moment> findLatestPerAuthor(UUID spaceId, Collection<UUID> authorIds) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return pickOnePer(jpaRepository.findLatestPerAuthor(spaceId, authorIds), Moment::authorId);
    }

    @Override
    public List<Moment> findLatestPerSpace(Collection<UUID> spaceIds, Collection<UUID> excludedAuthorIds) {
        if (spaceIds.isEmpty()) {
            return List.of();
        }
        if (excludedAuthorIds.isEmpty()) {
            throw new IllegalArgumentException("excludedAuthorIds must not be empty");
        }
        return pickOnePer(jpaRepository.findLatestPerSpace(spaceIds, excludedAuthorIds), Moment::spaceId);
    }

    // 같은 키(작성자 또는 스페이스)에 동시각 모먼트가 여럿이면 id가 가장 큰 것 하나만 남긴다.
    private static List<Moment> pickOnePer(List<MomentEntity> entities, Function<Moment, UUID> key) {
        Map<UUID, Moment> picked = entities.stream()
                .map(MomentEntity::toDomain)
                .collect(Collectors.toMap(key, Function.identity(),
                        (a, b) -> a.id().compareTo(b.id()) >= 0 ? a : b));
        return picked.values().stream()
                .sorted(Comparator.comparing(Moment::createdAt).reversed())
                .toList();
    }
}
