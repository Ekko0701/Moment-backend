package com.moment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppleLoginRequest(
        @NotBlank @Size(max = 8192) String identityToken,
        @NotBlank @Size(max = 2048) String authorizationCode,
        @NotBlank @Size(max = 256) String nonce,
        @Size(min = 2, max = 12) String nickname) {
}
