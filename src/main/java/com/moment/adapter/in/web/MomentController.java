package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.moment.MomentRequest;
import com.moment.adapter.in.web.dto.moment.PresignRequest;
import com.moment.adapter.in.web.dto.moment.MomentResponse;
import com.moment.adapter.in.web.dto.moment.PresignResponse;
import com.moment.application.port.in.MomentUseCase;
import com.moment.application.port.in.MomentUseCase.CreateMomentCommand;
import com.moment.application.port.in.MomentUseCase.MomentView;
import com.moment.application.port.in.MomentUseCase.TimelinePage;
import com.moment.common.response.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/moments")
public class MomentController {

    private final MomentUseCase momentUseCase;

    public MomentController(MomentUseCase momentUseCase) {
        this.momentUseCase = momentUseCase;
    }

    @PostMapping("/presign")
    ApiResponse<PresignResponse> presign(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PresignRequest request) {
        var response = momentUseCase.presign(userId, request.contentType());
        return ApiResponse.ok(new PresignResponse(response.uploadUrl(), response.imageUrl()));
    }

    @PostMapping
    ApiResponse<MomentResponse> create(
            @AuthenticationPrincipal UUID userId,
            @RequestBody MomentRequest request) {
        var command = new CreateMomentCommand(request.spaceId(), request.imageUrl(), request.text());
        var view = momentUseCase.create(userId, command);
        return ApiResponse.ok(buildResponse(view));
    }

    @GetMapping
    ApiResponse<List<MomentResponse>> timeline(
            @AuthenticationPrincipal UUID userId,
            @RequestParam UUID spaceId,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(defaultValue = "20") int limit) {
        TimelinePage page = momentUseCase.timeline(userId, spaceId, cursor, limit);
        var responses = page.items().stream().map(this::buildResponse).toList();
        return ApiResponse.ok(responses, new TimelineMetadata(page.nextCursor(), page.hasNext()));
    }

    @GetMapping("/latest")
    ApiResponse<MomentResponse> latest(
            @AuthenticationPrincipal UUID userId,
            @RequestParam UUID spaceId,
            @RequestParam(defaultValue = "false") boolean excludeMine) {
        var view = momentUseCase.latest(userId, spaceId, excludeMine);
        return ApiResponse.ok(view.map(this::buildResponse).orElse(null));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        momentUseCase.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private MomentResponse buildResponse(MomentView view) {
        return new MomentResponse(
                view.id(),
                view.spaceId(),
                new MomentResponse.AuthorResponse(
                        view.author().userId(),
                        view.author().handle(),
                        view.author().nickname(),
                        view.author().profileImageUrl()),
                view.imageUrl(),
                view.text(),
                view.createdAtMs(),
                view.reactions().stream()
                        .map(r -> new MomentResponse.ReactionCountResponse(r.emoji(), r.count()))
                        .toList(),
                view.myReaction());
    }

    record PresignResponse(String uploadUrl, String imageUrl) {
    }

    record TimelineMetadata(Instant nextCursor, boolean hasNext) {
    }
}
