package com.moment.domain.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * 리프레시 토큰 — 원문이 아닌 SHA-256 해시만 보관한다.
 */
public record RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant createdAt) {

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
