package com.moment.adapter.in.web.dto.space;

import com.moment.adapter.in.web.dto.moment.MomentResponse;
import com.moment.application.port.in.SpaceUseCase.SpaceView;
import java.util.List;
import java.util.UUID;

/**
 * @param myRole               요청자의 역할 (OWNER / MEMBER)
 * @param notificationsEnabled 요청자의 이 스페이스 알림 수신 여부
 * @param latestMoment         나와 차단 관계를 제외한, 다른 멤버의 최신 모먼트. 없으면 null
 * @param pendingJoinRequestCount 내가 방장인 그룹에 들어온 대기 중 참여 요청 수. 그 밖에는 0
 */
public record SpaceResponse(
        UUID id,
        String name,
        String type,
        String status,
        long connectedAt,
        List<MemberResponse> members,
        String myRole,
        int maxMembers,
        int memberCount,
        boolean notificationsEnabled,
        MomentResponse latestMoment,
        int pendingJoinRequestCount) {

    public static SpaceResponse from(SpaceView view) {
        return from(view, null);
    }

    public static SpaceResponse from(SpaceView view, MomentResponse latestMoment) {
        return new SpaceResponse(
                view.id(),
                view.name(),
                view.type(),
                view.status(),
                view.connectedAt(),
                view.members().stream()
                        .map(m -> new MemberResponse(m.userId(), m.handle(), m.nickname(), m.profileImageUrl(),
                                m.joinedAt()))
                        .toList(),
                view.myRole(),
                view.maxMembers(),
                view.members().size(),
                view.notificationsEnabled(),
                latestMoment,
                view.pendingJoinRequestCount());
    }

    public record MemberResponse(UUID userId, String handle, String nickname, String profileImageUrl, long joinedAt) {
    }
}
