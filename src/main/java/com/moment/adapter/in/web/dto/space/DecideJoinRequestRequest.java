package com.moment.adapter.in.web.dto.space;

import jakarta.validation.constraints.NotBlank;

/** @param action approve 또는 reject */
public record DecideJoinRequestRequest(
        @NotBlank(message = "action은 필수입니다.")
        String action) {
}
