package com.moment.domain.invite;

import java.time.Instant;
import java.util.UUID;

/**
 * 초대 코드 도메인 모델.
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record InviteCode(
        String code,
        UUID issuerUserId,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt) {

    /**
     * 코드가 만료되었는지 확인.
     */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
