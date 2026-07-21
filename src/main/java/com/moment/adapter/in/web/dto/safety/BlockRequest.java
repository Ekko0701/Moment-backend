package com.moment.adapter.in.web.dto.safety;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BlockRequest(
        @NotNull(message = "대상 사용자는 필수입니다.")
        UUID targetUserId) {
}
