package com.moment.application.service;

import com.moment.application.port.in.DeviceUseCase;
import com.moment.application.port.out.DevicePort;
import com.moment.domain.device.Device;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeviceService implements DeviceUseCase {

    private final DevicePort deviceRepository;
    private final Clock clock;

    public DeviceService(DevicePort deviceRepository, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Override
    public void register(UUID meId, String apnsToken) {
        Instant now = clock.instant();
        Device device = Device.register(UUID.randomUUID(), meId, apnsToken, now);
        deviceRepository.upsert(device);
    }
}
