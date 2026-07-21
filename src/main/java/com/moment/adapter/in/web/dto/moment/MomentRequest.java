package com.moment.adapter.in.web.dto.moment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MomentRequest(
        @NotNull(message = "spaceId는 필수입니다.")
        UUID spaceId,
        String imageUrl,
        @Size(max = 500, message = "글은 500자 이하로 입력해 주세요.")
        String text) {
}
