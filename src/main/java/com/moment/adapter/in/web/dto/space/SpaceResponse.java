package com.moment.adapter.in.web.dto.space;

import java.util.List;
import java.util.UUID;

public record SpaceResponse(
        UUID id,
        String type,
        String status,
        long connectedAt,
        List<MemberResponse> members) {

    public record MemberResponse(UUID userId, String handle, String nickname, String profileImageUrl, long joinedAt) {
    }
}
