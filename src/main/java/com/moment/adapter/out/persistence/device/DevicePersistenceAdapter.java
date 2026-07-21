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
        // apnsToken이 유니크 키다. 같은 토큰이 이미 있으면 소유자/갱신시각만 업데이트하고,
        // 없으면 새로 삽입한다. (id로 조회하면 매 등록마다 새 UUID라 항상 삽입되어
        // 유니크 제약 위반 500이 발생하던 문제를 해결)
        DeviceEntity entity = jpaRepository.findByApnsToken(device.apnsToken())
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
