package com.moment.adapter.in.web.dto.invite;

import jakarta.validation.constraints.NotBlank;

public record RespondInvitationRequest(
        @NotBlank(message = "action은 필수입니다.")
        String action) {
}
