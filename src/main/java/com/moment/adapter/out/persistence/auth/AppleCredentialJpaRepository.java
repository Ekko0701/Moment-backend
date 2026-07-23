package com.moment.adapter.out.persistence.auth;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppleCredentialJpaRepository extends JpaRepository<AppleCredentialEntity, UUID> {
}
