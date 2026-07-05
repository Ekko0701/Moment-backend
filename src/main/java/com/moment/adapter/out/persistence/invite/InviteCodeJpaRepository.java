package com.moment.adapter.out.persistence.invite;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InviteCodeJpaRepository extends JpaRepository<InviteCodeEntity, String> {

    List<InviteCodeEntity> findByIssuerUserId(UUID issuerUserId);
}
