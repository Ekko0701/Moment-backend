package com.moment.application.port.in;

import java.util.UUID;

/**
 * 디바이스 유스케이스 포트 (인바운드).
 */
public interface DeviceUseCase {

    /**
     * APNS 토큰 등록 또는 갱신.
     *
     * @param meId 현재 사용자 ID
     * @param apnsToken APNS 토큰
     */
    void register(UUID meId, String apnsToken);
}
