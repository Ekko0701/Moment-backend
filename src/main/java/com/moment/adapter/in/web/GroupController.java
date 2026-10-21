package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.invite.IssueCodeResponse;
import com.moment.adapter.in.web.dto.space.CreateGroupRequest;
import com.moment.adapter.in.web.dto.space.CreateGroupResponse;
import com.moment.adapter.in.web.dto.space.DecideJoinRequestRequest;
import com.moment.adapter.in.web.dto.space.JoinGroupRequest;
import com.moment.adapter.in.web.dto.space.JoinRequestResponse;
import com.moment.adapter.in.web.dto.space.PendingJoinRequestResponse;
import com.moment.adapter.in.web.dto.space.SpaceResponse;
import com.moment.application.port.in.GroupSpaceUseCase;
import com.moment.application.port.in.SpaceUseCase;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 그룹 스페이스 생성·참여 요청·초대 코드·종료. 멤버 관련 API는 SpaceController가 1:1과 함께 제공한다. */
@RestController
@RequestMapping("/v1/groups")
public class GroupController {

    private final GroupSpaceUseCase groupSpaceUseCase;
    private final SpaceUseCase spaceUseCase;

    public GroupController(GroupSpaceUseCase groupSpaceUseCase, SpaceUseCase spaceUseCase) {
        this.groupSpaceUseCase = groupSpaceUseCase;
        this.spaceUseCase = spaceUseCase;
    }

    @PostMapping
    ApiResponse<CreateGroupResponse> create(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateGroupRequest request) {
        var created = groupSpaceUseCase.create(userId, request.name());
        var space = SpaceResponse.from(spaceUseCase.getMySpace(userId, created.spaceId()));
        return ApiResponse.ok(new CreateGroupResponse(space, created.code(), created.expiresAt()));
    }

    /** 초대 코드로 참여를 요청한다. 방장이 수락하면 멤버가 된다. */
    @PostMapping("/join")
    ApiResponse<JoinRequestResponse> join(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody JoinGroupRequest request) {
        return ApiResponse.ok(JoinRequestResponse.from(groupSpaceUseCase.requestJoin(userId, request.code())));
    }

    /** 내가 보낸 대기 중 참여 요청. */
    @GetMapping("/join-requests/me")
    ApiResponse<List<JoinRequestResponse>> myJoinRequests(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupSpaceUseCase.myJoinRequests(userId).stream()
                .map(JoinRequestResponse::from)
                .toList());
    }

    /** 방장이 보는 대기 중 참여 요청. */
    @GetMapping("/{spaceId}/join-requests")
    ApiResponse<List<PendingJoinRequestResponse>> joinRequests(@AuthenticationPrincipal UUID userId,
            @PathVariable UUID spaceId) {
        return ApiResponse.ok(groupSpaceUseCase.joinRequests(userId, spaceId).stream()
                .map(PendingJoinRequestResponse::from)
                .toList());
    }

    /** 방장이 참여 요청을 수락(approve)하거나 거절(reject)한다. */
    @PatchMapping("/{spaceId}/join-requests/{requestId}")
    ApiResponse<Void> decide(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId,
            @PathVariable UUID requestId, @Valid @RequestBody DecideJoinRequestRequest request) {
        boolean approve = switch (request.action()) {
            case "approve" -> true;
            case "reject" -> false;
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "action은 approve 또는 reject예요.");
        };
        groupSpaceUseCase.decideJoinRequest(userId, spaceId, requestId, approve);
        return ApiResponse.ok();
    }

    /** 요청자가 자기 참여 요청을 취소한다. */
    @DeleteMapping("/{spaceId}/join-requests/{requestId}")
    ApiResponse<Void> cancel(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId,
            @PathVariable UUID requestId) {
        groupSpaceUseCase.cancelJoinRequest(userId, spaceId, requestId);
        return ApiResponse.ok();
    }

    @GetMapping("/{spaceId}/invite-code")
    ApiResponse<IssueCodeResponse> currentCode(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId) {
        var code = groupSpaceUseCase.currentCode(userId, spaceId);
        return ApiResponse.ok(new IssueCodeResponse(code.code(), code.expiresAt()));
    }

    @PostMapping("/{spaceId}/invite-code")
    ApiResponse<IssueCodeResponse> reissueCode(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId) {
        var code = groupSpaceUseCase.reissueCode(userId, spaceId);
        return ApiResponse.ok(new IssueCodeResponse(code.code(), code.expiresAt()));
    }

    @DeleteMapping("/{spaceId}")
    ApiResponse<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId) {
        groupSpaceUseCase.delete(userId, spaceId);
        return ApiResponse.ok();
    }
}
