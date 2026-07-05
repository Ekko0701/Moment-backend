package com.moment.adapter.out.persistence.space;

import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.space.SpaceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SpacePersistenceAdapter implements SpaceRepositoryPort {

    private final SpaceJpaRepository spaceJpaRepository;
    private final SpaceMemberJpaRepository memberJpaRepository;

    SpacePersistenceAdapter(
            SpaceJpaRepository spaceJpaRepository, SpaceMemberJpaRepository memberJpaRepository) {
        this.spaceJpaRepository = spaceJpaRepository;
        this.memberJpaRepository = memberJpaRepository;
    }

    @Override
    @Transactional
    public Space save(Space space) {
        SpaceEntity entity =
                spaceJpaRepository
                        .findById(space.id())
                        .map(
                                existing -> {
                                    existing.applyDomain(space);
                                    return existing;
                                })
                        .orElseGet(() -> SpaceEntity.fromDomain(space));
        return spaceJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Space> findById(UUID id) {
        return spaceJpaRepository.findById(id).map(SpaceEntity::toDomain);
    }

    @Override
    @Transactional
    public SpaceMember saveMember(SpaceMember member) {
        SpaceMemberEntity entity =
                memberJpaRepository
                        .findById(member.id())
                        .map(
                                existing -> {
                                    existing.applyDomain(member);
                                    return existing;
                                })
                        .orElseGet(() -> SpaceMemberEntity.fromDomain(member));
        return memberJpaRepository.save(entity).toDomain();
    }

    @Override
    public List<SpaceMember> findActiveMembersBySpaceId(UUID spaceId) {
        return memberJpaRepository.findBySpaceId(spaceId).stream()
                .filter(entity -> entity.getLeftAt() == null)
                .map(SpaceMemberEntity::toDomain)
                .toList();
    }

    @Override
    public List<SpaceMember> findActiveMembershipsByUserId(UUID userId) {
        return memberJpaRepository.findByUserIdAndLeftAtIsNull(userId).stream()
                .map(SpaceMemberEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsActiveOneToOneMembership(UUID userId) {
        return memberJpaRepository.findByUserIdAndLeftAtIsNull(userId).stream()
                .anyMatch(
                        member -> {
                            var space = spaceJpaRepository.findById(member.getSpaceId());
                            return space.isPresent() && space.get().getType() == SpaceType.ONE_TO_ONE;
                        });
    }
}
