package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.invite.IssueCodeResponse;
import com.moment.adapter.in.web.dto.space.CreateGroupRequest;
import com.moment.adapter.in.web.dto.space.CreateGroupResponse;
import com.moment.adapter.in.web.dto.space.JoinGroupRequest;
import com.moment.adapter.in.web.dto.space.SpaceResponse;
import com.moment.application.port.in.GroupSpaceUseCase;
import com.moment.application.port.in.SpaceUseCase;
import com.moment.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 그룹 스페이스 생성·참여·초대 코드·종료. 멤버 관련 API는 SpaceController가 1:1과 함께 제공한다. */
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

    @PostMapping("/join")
    ApiResponse<SpaceResponse> join(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody JoinGroupRequest request) {
        UUID spaceId = groupSpaceUseCase.join(userId, request.code());
        return ApiResponse.ok(SpaceResponse.from(spaceUseCase.getMySpace(userId, spaceId)));
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
