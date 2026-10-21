package com.moment.adapter.in.web.dto.space;

import com.moment.application.port.in.GroupSpaceUseCase.PendingJoinRequestView;
import java.util.UUID;

/** 방장에게 보이는 대기 중 참여 요청과 요청자 정보. */
public record PendingJoinRequestResponse(
        UUID id,
        UUID userId,
        String handle,
        String nickname,
        String profileImageUrl,
        long createdAt) {

    public static PendingJoinRequestResponse from(PendingJoinRequestView view) {
        return new PendingJoinRequestResponse(view.id(), view.userId(), view.handle(), view.nickname(),
                view.profileImageUrl(), view.createdAt());
    }
}
