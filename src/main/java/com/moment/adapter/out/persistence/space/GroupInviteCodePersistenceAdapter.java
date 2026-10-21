package com.moment.adapter.out.persistence.space;

import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.domain.space.GroupInviteCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class GroupInviteCodePersistenceAdapter implements GroupInviteCodePort {

    private final GroupInviteCodeJpaRepository jpaRepository;

    GroupInviteCodePersistenceAdapter(GroupInviteCodeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public GroupInviteCode save(GroupInviteCode code) {
        return jpaRepository.save(GroupInviteCodeEntity.fromDomain(code)).toDomain();
    }

    @Override
    public Optional<GroupInviteCode> findByCode(String code) {
        return jpaRepository.findById(code).map(GroupInviteCodeEntity::toDomain);
    }

    @Override
    public Optional<GroupInviteCode> findBySpaceId(UUID spaceId) {
        return jpaRepository.findBySpaceId(spaceId).map(GroupInviteCodeEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteBySpaceId(UUID spaceId) {
        jpaRepository.deleteBySpaceId(spaceId);
    }
}
