package com.moment.adapter.in.web.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceRequest(
        @NotBlank(message = "APNs 토큰은 필수입니다.")
        @Size(max = 200, message = "APNs 토큰이 너무 깁니다.")
        String apnsToken) {
}
