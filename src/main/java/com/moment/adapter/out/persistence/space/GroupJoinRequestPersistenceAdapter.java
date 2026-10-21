package com.moment.adapter.out.persistence.space;

import com.moment.application.port.out.GroupJoinRequestPort;
import com.moment.domain.space.GroupJoinRequest;
import com.moment.domain.space.JoinRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class GroupJoinRequestPersistenceAdapter implements GroupJoinRequestPort {

    private final GroupJoinRequestJpaRepository jpaRepository;

    GroupJoinRequestPersistenceAdapter(GroupJoinRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public GroupJoinRequest save(GroupJoinRequest request) {
        return jpaRepository.save(GroupJoinRequestEntity.fromDomain(request)).toDomain();
    }

    @Override
    public Optional<GroupJoinRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(GroupJoinRequestEntity::toDomain);
    }

    @Override
    public Optional<GroupJoinRequest> findPending(UUID spaceId, UUID userId) {
        return jpaRepository.findFirstBySpaceIdAndUserIdAndStatus(spaceId, userId, JoinRequestStatus.PENDING)
                .map(GroupJoinRequestEntity::toDomain);
    }

    @Override
    public List<GroupJoinRequest> findPendingBySpaceId(UUID spaceId) {
        return jpaRepository.findBySpaceIdAndStatusOrderByCreatedAtAsc(spaceId, JoinRequestStatus.PENDING).stream()
                .map(GroupJoinRequestEntity::toDomain)
                .toList();
    }

    @Override
    public Map<UUID, Long> countPendingBySpaceIds(Collection<UUID> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.countBySpaceIdsAndStatus(spaceIds, JoinRequestStatus.PENDING).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    @Override
    public List<GroupJoinRequest> findPendingByUserId(UUID userId) {
        return jpaRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, JoinRequestStatus.PENDING).stream()
                .map(GroupJoinRequestEntity::toDomain)
                .toList();
    }
}
