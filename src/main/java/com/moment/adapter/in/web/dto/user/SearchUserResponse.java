package com.moment.adapter.in.web.dto.user;

import java.util.UUID;

public record SearchUserResponse(UUID userId, String handle, String nickname, String profileImageUrl) {
}
