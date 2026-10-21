package com.moment.application.port.in;

import java.util.List;
import java.util.UUID;

public interface SpaceUseCase {

    List<SpaceView> getMySpaces(UUID meId);

    /** 내가 활성 멤버인 스페이스 하나의 뷰. 멤버가 아니면 FORBIDDEN. */
    SpaceView getMySpace(UUID meId, UUID spaceId);

    void leave(UUID meId, UUID spaceId);

    record SpaceView(
            UUID id,
            String name,
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

    void rename(UUID meId, UUID spaceId, String name);
}
