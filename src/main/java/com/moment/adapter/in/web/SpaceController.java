package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.space.SpaceResponse;
import com.moment.application.port.in.SpaceUseCase;
import com.moment.common.response.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/spaces")
public class SpaceController {

    private final SpaceUseCase spaceUseCase;

    public SpaceController(SpaceUseCase spaceUseCase) {
        this.spaceUseCase = spaceUseCase;
    }

    @GetMapping("/me")
    ApiResponse<List<SpaceResponse>> getMySpaces(@AuthenticationPrincipal UUID userId) {
        var spaceViews = spaceUseCase.getMySpaces(userId);
        var responses = spaceViews.stream()
                .map(view -> new SpaceResponse(
                        view.id(),
                        view.type(),
                        view.status(),
                        view.connectedAt(),
                        view.members().stream()
                                .map(m -> new SpaceResponse.MemberResponse(
                                        m.userId(),
                                        m.handle(),
                                        m.nickname(),
                                        m.profileImageUrl(),
                                        m.joinedAt()))
                                .toList()))
                .toList();
        return ApiResponse.ok(responses);
    }

    @DeleteMapping("/{spaceId}/members/me")
    ApiResponse<Void> leave(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId) {
        spaceUseCase.leave(userId, spaceId);
        return ApiResponse.ok();
    }
}
