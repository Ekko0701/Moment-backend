package com.moment.adapter.in.web.dto.invite;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        UUID fromUserId,
        UUID toUserId,
        String via,
        String status,
        Instant createdAt,
        Instant respondedAt,
        UserSummary counterpart) {

    public record UserSummary(UUID userId, String handle, String nickname, String profileImageUrl) {
    }
}
