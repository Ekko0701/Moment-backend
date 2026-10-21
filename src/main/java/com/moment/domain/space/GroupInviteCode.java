package com.moment.domain.space;

import java.time.Instant;
import java.util.UUID;

/**
 * 그룹 스페이스 초대 코드. 스페이스당 하나만 유효하며 만료 전까지 여러 사람이 재사용한다.
 * 불변 객체.
 */
public record GroupInviteCode(
        String code,
        UUID spaceId,
        UUID issuedBy,
        Instant expiresAt,
        Instant createdAt) {

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
