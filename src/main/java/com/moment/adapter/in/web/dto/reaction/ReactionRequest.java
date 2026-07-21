package com.moment.adapter.in.web.dto.reaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionRequest(
        @NotBlank(message = "이모지는 필수입니다.")
        @Size(max = 16, message = "이모지가 너무 깁니다.")
        String emoji) {
}
