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
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/v1")
public class MomentController {

    private final MomentUseCase momentUseCase;

    public MomentController(MomentUseCase momentUseCase) {
        this.momentUseCase = momentUseCase;
    }

    @PostMapping("/moments/presign")
    ApiResponse<PresignResponse> presign(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PresignRequest request) {
        var response = momentUseCase.presign(userId, request.contentType());
        return ApiResponse.ok(new PresignResponse(response.uploadUrl(), response.imageUrl()));
    }

    @PostMapping("/moments")
    ApiResponse<MomentResponse> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody MomentRequest request) {
        var command = new CreateMomentCommand(request.spaceId(), request.imageUrl(), request.text());
        var view = momentUseCase.create(userId, command);
        return ApiResponse.ok(buildResponse(view));
    }

    // 기획서 7장 계약: GET /v1/spaces/{spaceId}/moments?cursor=&limit=
    @GetMapping("/spaces/{spaceId}/moments")
    ApiResponse<List<MomentResponse>> timeline(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        TimelinePage page = momentUseCase.timeline(userId, spaceId, cursor, limit);
        var responses = page.items().stream().map(this::buildResponse).toList();
        return ApiResponse.ok(responses, new TimelineMetadata(page.nextCursor(), page.hasNext()));
    }

    // 기획서 7장 계약: GET /v1/spaces/{spaceId}/moments/latest?excludeMine=
    @GetMapping("/spaces/{spaceId}/moments/latest")
    ApiResponse<MomentResponse> latest(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID spaceId,
            @RequestParam(defaultValue = "false") boolean excludeMine) {
        var view = momentUseCase.latest(userId, spaceId, excludeMine);
        return ApiResponse.ok(view.map(this::buildResponse).orElse(null));
    }

    // 전 API 공통 엔벨로프 계약 유지 — 204 빈 응답이면 클라이언트 엔벨로프 파싱이 깨진다
    @DeleteMapping("/moments/{id}")
    ApiResponse<Void> delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        momentUseCase.delete(userId, id);
        return ApiResponse.ok();
    }

    private MomentResponse buildResponse(MomentView view) {
        return MomentResponse.from(view);
    }

    record PresignResponse(String uploadUrl, String imageUrl) {
    }

    record TimelineMetadata(String nextCursor, boolean hasNext) {
    }
}
