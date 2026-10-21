package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.moment.MomentResponse;
import com.moment.adapter.in.web.dto.space.GroupMemberResponse;
import com.moment.adapter.in.web.dto.space.RenameSpaceRequest;
import com.moment.adapter.in.web.dto.space.SpaceResponse;
import com.moment.adapter.in.web.dto.space.UpdateMembershipRequest;
import com.moment.application.port.in.GroupSpaceUseCase;
import com.moment.application.port.in.MomentUseCase;
import com.moment.application.port.in.MomentUseCase.MomentView;
import com.moment.application.port.in.SpaceUseCase;
import com.moment.common.response.ApiResponse;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/spaces")
public class SpaceController {

    private final SpaceUseCase spaceUseCase;
    private final GroupSpaceUseCase groupSpaceUseCase;
    private final MomentUseCase momentUseCase;

    public SpaceController(SpaceUseCase spaceUseCase, GroupSpaceUseCase groupSpaceUseCase,
            MomentUseCase momentUseCase) {
        this.spaceUseCase = spaceUseCase;
        this.groupSpaceUseCase = groupSpaceUseCase;
        this.momentUseCase = momentUseCase;
    }

    /** 내 스페이스 목록과 스페이스별 최신 모먼트(나 제외). 홈 화면 한 장을 이 응답 하나로 그린다. */
    @GetMapping("/me")
    ApiResponse<List<SpaceResponse>> getMySpaces(@AuthenticationPrincipal UUID userId) {
        Map<UUID, MomentView> latestBySpace = momentUseCase.latestForMySpaces(userId).stream()
                .collect(Collectors.toMap(MomentView::spaceId, Function.identity()));
        var responses = spaceUseCase.getMySpaces(userId).stream()
                .map(view -> SpaceResponse.from(view,
                        latestBySpace.containsKey(view.id()) ? MomentResponse.from(latestBySpace.get(view.id())) : null))
                .toList();
        return ApiResponse.ok(responses);
    }

    @DeleteMapping("/{spaceId}/members/me")
    ApiResponse<Void> leave(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId) {
        spaceUseCase.leave(userId, spaceId);
        return ApiResponse.ok();
    }

    @PatchMapping("/{spaceId}/members/me")
    ApiResponse<Void> updateMyMembership(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId,
            @Valid @RequestBody UpdateMembershipRequest request) {
        groupSpaceUseCase.setNotifications(userId, spaceId, request.notificationsEnabled());
        return ApiResponse.ok();
    }

    /** 활성 멤버 목록과 멤버별 최신 모먼트. */
    @GetMapping("/{spaceId}/members")
    ApiResponse<List<GroupMemberResponse>> members(@AuthenticationPrincipal UUID userId,
            @PathVariable UUID spaceId) {
        Map<UUID, MomentView> latestByAuthor = momentUseCase.latestPerMember(userId, spaceId).stream()
                .collect(Collectors.toMap(v -> v.author().userId(), Function.identity()));
        var responses = groupSpaceUseCase.members(userId, spaceId).stream()
                .map(m -> new GroupMemberResponse(
                        m.userId(),
                        m.handle(),
                        m.nickname(),
                        m.profileImageUrl(),
                        m.role(),
                        m.joinedAt(),
                        latestByAuthor.containsKey(m.userId())
                                ? MomentResponse.from(latestByAuthor.get(m.userId()))
                                : null))
                .toList();
        return ApiResponse.ok(responses);
    }

    @DeleteMapping("/{spaceId}/members/{targetUserId}")
    ApiResponse<Void> kick(@AuthenticationPrincipal UUID userId, @PathVariable UUID spaceId,
            @PathVariable UUID targetUserId) {
        groupSpaceUseCase.kick(userId, spaceId, targetUserId);
        return ApiResponse.ok();
    }

    @PatchMapping("/{spaceId}")
    ApiResponse<Void> rename(
        @AuthenticationPrincipal UUID userId,
        @PathVariable UUID spaceId,
        @Valid @RequestBody RenameSpaceRequest request) {

        spaceUseCase.rename(userId, spaceId, request.name());
        return ApiResponse.ok();
    }
}
