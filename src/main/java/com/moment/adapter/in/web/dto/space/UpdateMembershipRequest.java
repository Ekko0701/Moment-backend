package com.moment.adapter.in.web.dto.space;

import jakarta.validation.constraints.NotNull;

public record UpdateMembershipRequest(
        @NotNull(message = "알림 설정 값이 필요해요.") Boolean notificationsEnabled) {
}
