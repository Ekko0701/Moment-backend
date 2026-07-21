package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.reaction.ReactionRequest;
import com.moment.application.port.in.ReactionUseCase;
import com.moment.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/moments/{momentId}/reaction")
public class ReactionController {

    private final ReactionUseCase reactionUseCase;

    public ReactionController(ReactionUseCase reactionUseCase) {
        this.reactionUseCase = reactionUseCase;
    }

    @PutMapping
    ApiResponse<Void> react(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID momentId,
            @RequestBody ReactionRequest request) {
        reactionUseCase.react(userId, momentId, request.emoji());
        return ApiResponse.ok();
    }

    @DeleteMapping
    ApiResponse<Void> remove(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID momentId) {
        reactionUseCase.remove(userId, momentId);
        return ApiResponse.ok();
    }
}
