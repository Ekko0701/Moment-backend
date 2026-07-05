package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.invite.IssueCodeResponse;
import com.moment.application.port.in.InvitationUseCase;
import com.moment.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/invite-codes")
public class InviteCodeController {

    private final InvitationUseCase invitationUseCase;

    public InviteCodeController(InvitationUseCase invitationUseCase) {
        this.invitationUseCase = invitationUseCase;
    }

    @PostMapping
    ApiResponse<IssueCodeResponse> issueCode(@AuthenticationPrincipal UUID userId) {
        var issued = invitationUseCase.issueCode(userId);
        return ApiResponse.ok(new IssueCodeResponse(issued.code(), issued.expiresAt()));
    }
}
