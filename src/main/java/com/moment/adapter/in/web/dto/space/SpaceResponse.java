package com.moment.adapter.in.web.dto.space;

import com.moment.application.port.in.SpaceUseCase.SpaceView;
import java.util.List;
import java.util.UUID;

public record SpaceResponse(
        UUID id,
        String name,
        String type,
        String status,
        long connectedAt,
        List<MemberResponse> members) {

    public static SpaceResponse from(SpaceView view) {
        return new SpaceResponse(
                view.id(),
                view.name(),
                view.type(),
                view.status(),
                view.connectedAt(),
                view.members().stream()
                        .map(m -> new MemberResponse(m.userId(), m.handle(), m.nickname(), m.profileImageUrl(),
                                m.joinedAt()))
                        .toList());
    }

    public record MemberResponse(UUID userId, String handle, String nickname, String profileImageUrl, long joinedAt) {
    }
}
