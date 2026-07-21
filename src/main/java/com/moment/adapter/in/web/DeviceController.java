package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.device.DeviceRequest;
import com.moment.application.port.in.DeviceUseCase;
import com.moment.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/devices")
public class DeviceController {

    private final DeviceUseCase deviceUseCase;

    public DeviceController(DeviceUseCase deviceUseCase) {
        this.deviceUseCase = deviceUseCase;
    }

    @PostMapping
    ApiResponse<Void> register(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody DeviceRequest request) {
        deviceUseCase.register(userId, request.apnsToken());
        return ApiResponse.ok();
    }
}
