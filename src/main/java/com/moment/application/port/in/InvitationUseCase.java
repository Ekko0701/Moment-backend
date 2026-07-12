package com.moment.application.port.in;

import com.moment.domain.invite.SpaceInvitation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InvitationUseCase {

    IssuedCode issueCode(UUID meId);

    SpaceInvitation send(UUID meId, SendCommand command);

    List<SpaceInvitation> list(UUID meId, boolean received);

    void respond(UUID meId, UUID invitationId, RespondCommand command);

    record IssuedCode(String code, Instant expiresAt) {
    }

    record SendCommand(UUID toUserId, String code) {
    }

    record RespondCommand(String action) {
    }
}
