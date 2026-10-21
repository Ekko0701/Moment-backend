package com.moment.adapter.in.web.dto.space;

import com.moment.application.port.in.GroupSpaceUseCase.JoinRequestView;
import java.util.UUID;

/**
 * 요청자에게 보이는 그룹 참여 요청.
 *
 * @param status PENDING / APPROVED / REJECTED / CANCELED
 */
public record JoinRequestResponse(UUID id, UUID spaceId, String spaceName, String status, long createdAt) {

    public static JoinRequestResponse from(JoinRequestView view) {
        return new JoinRequestResponse(view.id(), view.spaceId(), view.spaceName(), view.status(), view.createdAt());
    }
}
