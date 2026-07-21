package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.invite.SendInvitationRequest;
import com.moment.adapter.in.web.dto.invite.RespondInvitationRequest;
import com.moment.adapter.in.web.dto.invite.InvitationResponse;
import com.moment.application.port.in.InvitationUseCase;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.response.ApiResponse;
import com.moment.domain.invite.SpaceInvitation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/invitations")
public class InvitationController {

    private final InvitationUseCase invitationUseCase;
    private final UserRepositoryPort userRepository;

    public InvitationController(InvitationUseCase invitationUseCase, UserRepositoryPort userRepository) {
        this.invitationUseCase = invitationUseCase;
        this.userRepository = userRepository;
    }

    @PostMapping
    ApiResponse<InvitationResponse> sendInvitation(@AuthenticationPrincipal UUID userId,
            @RequestBody SendInvitationRequest request) {
        SpaceInvitation invitation = invitationUseCase.send(userId,
                new InvitationUseCase.SendCommand(request.toUserId(), request.code()));
        // 보낸 사람 관점의 상대방 = 초대 대상 (iOS가 생성된 초대를 즉시 표시할 수 있도록 반환)
        var counterpart = userRepository.findById(invitation.toUserId()).orElseThrow();
        return ApiResponse.ok(new InvitationResponse(
                invitation.id(),
                invitation.fromUserId(),
                invitation.toUserId(),
                invitation.via().name(),
                invitation.status().name(),
                invitation.createdAt(),
                invitation.respondedAt(),
                new InvitationResponse.UserSummary(counterpart.id(), counterpart.handle(),
                        counterpart.nickname(), counterpart.profileImageUrl())));
    }

    @GetMapping
    ApiResponse<List<InvitationResponse>> list(@AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "true") boolean received) {
        List<SpaceInvitation> invitations = invitationUseCase.list(userId, received);
        List<InvitationResponse> responses = invitations.stream()
                .map(inv -> {
                    UUID counterpartId = received ? inv.fromUserId() : inv.toUserId();
                    var user = userRepository.findById(counterpartId).orElseThrow();
                    return new InvitationResponse(
                            inv.id(),
                            inv.fromUserId(),
                            inv.toUserId(),
                            inv.via().name(),
                            inv.status().name(),
                            inv.createdAt(),
                            inv.respondedAt(),
                            new InvitationResponse.UserSummary(user.id(), user.handle(), user.nickname(), user.profileImageUrl()));
                })
                .toList();
        return ApiResponse.ok(responses);
    }

    // 204 대신 200 엔벨로프 — 클라이언트가 모든 응답을 ApiResponse로 일관 파싱한다 (DELETE와 동일 규약)
    @PatchMapping("/{id}")
    ApiResponse<Void> respond(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @RequestBody RespondInvitationRequest request) {
        invitationUseCase.respond(userId, id, new InvitationUseCase.RespondCommand(request.action()));
        return ApiResponse.ok();
    }
}
