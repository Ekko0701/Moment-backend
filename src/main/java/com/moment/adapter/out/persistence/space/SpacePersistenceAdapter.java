package com.moment.adapter.out.persistence.space;

import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.space.SpaceStatus;
import com.moment.domain.space.SpaceType;
import java.util.Collection;
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
    public Optional<Space> findByIdForUpdate(UUID id) {
        return spaceJpaRepository.findByIdForUpdate(id).map(SpaceEntity::toDomain);
    }

    @Override
    public List<Space> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return spaceJpaRepository.findAllById(ids).stream().map(SpaceEntity::toDomain).toList();
    }

    @Override
    public Optional<SpaceMember> findMember(UUID spaceId, UUID userId) {
        return memberJpaRepository.findBySpaceIdAndUserId(spaceId, userId).map(SpaceMemberEntity::toDomain);
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
        // 멤버십(leftAt == null)뿐 아니라 스페이스가 ACTIVE인지도 확인한다.
        // 비활성 스페이스에 멤버십이 남아 있어도 새 연결을 막지 않도록 하기 위함.
        return memberJpaRepository.findByUserIdAndLeftAtIsNull(userId).stream()
                .anyMatch(
                        member ->
                                spaceJpaRepository
                                        .findById(member.getSpaceId())
                                        .map(SpaceEntity::toDomain)
                                        .filter(space -> space.type() == SpaceType.ONE_TO_ONE)
                                        .filter(space -> space.status() == SpaceStatus.ACTIVE)
                                        .isPresent());
    }
}
