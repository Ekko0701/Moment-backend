package com.moment.adapter.out.persistence.device;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {

    void deleteAllByUserId(UUID userId);
}
