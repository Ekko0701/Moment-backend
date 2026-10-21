package com.moment.application.port.in;

import java.util.List;
import java.util.UUID;

public interface SpaceUseCase {

    /** 내가 활성 멤버인 스페이스 목록. 1:1 스페이스가 먼저, 그다음 그룹을 생성 순으로. */
    List<SpaceView> getMySpaces(UUID meId);

    /** 내가 활성 멤버인 스페이스 하나의 뷰. 멤버가 아니면 FORBIDDEN. */
    SpaceView getMySpace(UUID meId, UUID spaceId);

    /**
     * 스페이스에서 나간다. 1:1은 양쪽이 모두 종료되고, 그룹은 나만 나간다.
     * 그룹 방장이 나가면 가장 먼저 들어온 멤버가 방장이 되고, 마지막 멤버가 나가면 그룹이 비활성화된다.
     */
    void leave(UUID meId, UUID spaceId);

    /** 이름 변경. 1:1은 멤버 누구나, 그룹은 방장만. */
    void rename(UUID meId, UUID spaceId, String name);

    /**
     * @param myRole               요청자의 역할 (OWNER / MEMBER)
     * @param notificationsEnabled 요청자의 이 스페이스 알림 수신 여부
     */
    record SpaceView(
            UUID id,
            String name,
            String type,
            String status,
            long connectedAt,
            List<MemberView> members,
            String myRole,
            int maxMembers,
            boolean notificationsEnabled) {
    }

    record MemberView(
            UUID userId,
            String handle,
            String nickname,
            String profileImageUrl,
            long joinedAt) {
    }
}
