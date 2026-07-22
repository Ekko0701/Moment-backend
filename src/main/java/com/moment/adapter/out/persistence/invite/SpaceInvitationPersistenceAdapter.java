package com.moment.adapter.out.persistence.invite;

import com.moment.application.port.out.SpaceInvitationPort;
import com.moment.domain.invite.InvitationStatus;
import com.moment.domain.invite.SpaceInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SpaceInvitationPersistenceAdapter implements SpaceInvitationPort {

    private final SpaceInvitationJpaRepository jpaRepository;

    SpaceInvitationPersistenceAdapter(SpaceInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public SpaceInvitation save(SpaceInvitation invitation) {
        SpaceInvitationEntity entity = SpaceInvitationEntity.fromDomain(invitation);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<SpaceInvitation> findById(UUID id) {
        return jpaRepository.findById(id).map(SpaceInvitationEntity::toDomain);
    }

    @Override
    public Optional<SpaceInvitation> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(SpaceInvitationEntity::toDomain);
    }

    @Override
    public boolean existsPendingBetween(UUID a, UUID b, Instant since) {
        return jpaRepository.existsPendingBetween(a, b, since);
    }

    @Override
    public List<SpaceInvitation> findAllByUser(UUID userId, boolean received) {
        if (received) {
            return jpaRepository.findByToUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(SpaceInvitationEntity::toDomain)
                    .toList();
        } else {
            return jpaRepository.findByFromUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(SpaceInvitationEntity::toDomain)
                    .toList();
        }
    }

    @Override
    public List<SpaceInvitation> findAllPendingByUserId(UUID userId) {
        return jpaRepository.findAllPendingByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SpaceInvitationEntity::toDomain)
                .toList();
    }

    @Override
    public int countByFromUserIdSince(UUID fromUserId, Instant since) {
        return jpaRepository.countByFromUserIdSince(fromUserId, since);
    }

    @Override
    public boolean existsDeclinedBetweenSince(UUID from, UUID to, Instant since) {
        return jpaRepository.existsDeclinedBetweenSince(from, to, since);
    }
}
