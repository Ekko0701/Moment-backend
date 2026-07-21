package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.moment.application.port.out.DevicePort;
import com.moment.domain.device.Device;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("DeviceService")
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Mock private DevicePort deviceRepository;

    private DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(deviceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("register: apnsToken·사용자·갱신시각을 담아 upsert한다")
    void register_upsertsDeviceWithToken() {
        UUID meId = UUID.randomUUID();

        service.register(meId, "apns-token-123");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).upsert(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(meId);
        assertThat(saved.apnsToken()).isEqualTo("apns-token-123");
        assertThat(saved.updatedAt()).isEqualTo(NOW);
    }
}
