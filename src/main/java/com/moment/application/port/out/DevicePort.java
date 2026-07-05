package com.moment.application.port.out;

import com.moment.domain.device.Device;
import java.util.UUID;

/**
 * 디바이스 저장소 포트 (아웃바운드).
 */
public interface DevicePort {

    /**
     * 디바이스 등록 또는 업데이트.
     * apnsToken이 유니크이므로, 같은 토큰이면 userId/updatedAt 갱신한다.
     */
    Device upsert(Device device);

    /**
     * 사용자의 모든 디바이스 토큰 삭제.
     */
    void deleteAllByUserId(UUID userId);
}
