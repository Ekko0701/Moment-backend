package com.moment.adapter.out.persistence.device;

import com.moment.application.port.out.DevicePort;
import com.moment.domain.device.Device;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class DevicePersistenceAdapter implements DevicePort {

    private final DeviceJpaRepository jpaRepository;

    DevicePersistenceAdapter(DeviceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Device upsert(Device device) {
        DeviceEntity entity = jpaRepository.findById(device.id())
                .map(existing -> {
                    existing.applyDomain(device);
                    return existing;
                })
                .orElseGet(() -> DeviceEntity.fromDomain(device));
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        jpaRepository.deleteAllByUserId(userId);
    }
}
