package com.moment.domain.device;

import java.time.Instant;
import java.util.UUID;

/**
 * 디바이스 도메인 모델 (기능정의서 F-05).
 * APNS 토큰을 관리하여 푸시 알림을 전송한다.
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record Device(
        UUID id,
        UUID userId,
        String apnsToken,
        Instant updatedAt) {

    /**
     * 새 디바이스 등록.
     */
    public static Device register(UUID id, UUID userId, String apnsToken, Instant now) {
        return new Device(id, userId, apnsToken, now);
    }

    /**
     * 토큰 갱신 (업데이트 시간 갱신).
     */
    public Device updateToken(String newApnsToken, Instant now) {
        return new Device(id, userId, newApnsToken, now);
    }
}
