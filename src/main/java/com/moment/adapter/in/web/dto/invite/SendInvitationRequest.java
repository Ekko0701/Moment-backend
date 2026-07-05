package com.moment.adapter.in.web.dto.invite;

import java.util.UUID;

public record SendInvitationRequest(UUID toUserId, String code) {
}
