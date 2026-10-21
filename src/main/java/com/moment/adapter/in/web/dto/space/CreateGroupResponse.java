package com.moment.adapter.in.web.dto.space;

import java.time.Instant;

public record CreateGroupResponse(SpaceResponse space, String inviteCode, Instant expiresAt) {
}
