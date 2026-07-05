package com.moment.adapter.out.persistence.device;

import com.moment.domain.device.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "apns_token", nullable = false, unique = true, length = 200)
    private String apnsToken;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    static DeviceEntity fromDomain(Device device) {
        DeviceEntity entity = new DeviceEntity();
        entity.id = device.id();
        entity.applyDomain(device);
        return entity;
    }

    void applyDomain(Device device) {
        this.userId = device.userId();
        this.apnsToken = device.apnsToken();
        this.updatedAt = device.updatedAt();
    }

    Device toDomain() {
        return new Device(id, userId, apnsToken, updatedAt);
    }
}
