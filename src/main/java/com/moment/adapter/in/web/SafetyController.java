package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.safety.BlockRequest;
import com.moment.adapter.in.web.dto.safety.ReportRequest;
import com.moment.application.port.in.SafetyUseCase;
import com.moment.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class SafetyController {

    private final SafetyUseCase safetyUseCase;

    public SafetyController(SafetyUseCase safetyUseCase) {
        this.safetyUseCase = safetyUseCase;
    }

    @PostMapping("/reports")
    ApiResponse<Void> report(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ReportRequest request) {
        safetyUseCase.report(userId, request.momentId(), request.reason(), request.detail());
        return ApiResponse.ok();
    }

    @PostMapping("/blocks")
    ApiResponse<Void> block(
            @AuthenticationPrincipal UUID userId,
            @RequestBody BlockRequest request) {
        safetyUseCase.block(userId, request.targetUserId());
        return ApiResponse.ok();
    }
}
