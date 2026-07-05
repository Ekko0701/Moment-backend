package com.moment.adapter.out.persistence.invite;

import com.moment.application.port.out.InviteCodePort;
import com.moment.domain.invite.InviteCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class InviteCodePersistenceAdapter implements InviteCodePort {

    private final InviteCodeJpaRepository jpaRepository;

    InviteCodePersistenceAdapter(InviteCodeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public InviteCode save(InviteCode code) {
        InviteCodeEntity entity = InviteCodeEntity.fromDomain(code);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<InviteCode> findByCode(String code) {
        return jpaRepository.findById(code).map(InviteCodeEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteAllByIssuerUserId(UUID issuerUserId) {
        var codes = jpaRepository.findByIssuerUserId(issuerUserId);
        jpaRepository.deleteAll(codes);
    }
}
