package com.moment.adapter.out.persistence.device;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {

    Optional<DeviceEntity> findByApnsToken(String apnsToken);

    void deleteAllByUserId(UUID userId);
}
