package com.moment.application.port.in;

import java.util.List;
import java.util.UUID;

public interface SpaceUseCase {

    List<SpaceView> getMySpaces(UUID meId);

    void leave(UUID meId, UUID spaceId);

    record SpaceView(
            UUID id,
            String type,
            String status,
            long connectedAt,
            List<MemberView> members) {
    }

    record MemberView(
            UUID userId,
            String handle,
            String nickname,
            String profileImageUrl,
            long joinedAt) {
    }
}
