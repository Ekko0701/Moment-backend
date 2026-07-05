package com.moment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppleLoginRequest(
        @NotBlank String identityToken,
        @Size(min = 2, max = 12) String nickname) {
}
