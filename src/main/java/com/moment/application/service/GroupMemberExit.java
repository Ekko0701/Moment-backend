package com.moment.application.service;

import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 그룹에서 멤버 한 명이 빠지는 절차. 그룹 나가기와 회원 탈퇴가 함께 쓴다.
 *
 * <p>스페이스 행을 잠근 뒤 처리해 참여·내보내기·종료와 직렬화한다.
 * 남은 멤버가 없으면 그룹을 종료하고 초대 코드를 지운다.
 * 남은 멤버 중 방장이 없으면 가장 먼저 들어온 멤버를 방장으로 올리고 OWNER_CHANGED 푸시를 보낸다.
 * 방장 여부는 잠금 이후 남은 멤버로 판단하므로, 호출 측이 오래된 멤버십을 들고 있어도 방장 없는 그룹이 남지 않는다.
 */
@Component
public class GroupMemberExit {

    private final SpaceRepositoryPort spaceRepository;
    private final GroupInviteCodePort groupInviteCodePort;
    private final PushPort pushPort;

    public GroupMemberExit(SpaceRepositoryPort spaceRepository, GroupInviteCodePort groupInviteCodePort,
            PushPort pushPort) {
        this.spaceRepository = spaceRepository;
        this.groupInviteCodePort = groupInviteCodePort;
        this.pushPort = pushPort;
    }

    /**
     * 사용자를 그룹에서 뺀다.
     *
     * @return 활성 멤버였다가 빠졌으면 true, 스페이스가 없거나 이미 활성 멤버가 아니면 false
     */
    public boolean exit(UUID spaceId, UUID userId, Instant now) {
        Space space = spaceRepository.findByIdForUpdate(spaceId).orElse(null);
        if (space == null) {
            return false;
        }
        SpaceMember me = spaceRepository.findMember(spaceId, userId)
                .filter(SpaceMember::isActive)
                .orElse(null);
        if (me == null) {
            return false;
        }

        spaceRepository.saveMember(me.leave(now));

        List<SpaceMember> remaining = spaceRepository.findActiveMembersBySpaceId(spaceId).stream()
                .filter(m -> !m.userId().equals(userId))
                .toList();

        if (remaining.isEmpty()) {
            spaceRepository.save(space.deactivate());
            groupInviteCodePort.deleteBySpaceId(spaceId);
            return true;
        }

        if (remaining.stream().noneMatch(SpaceMember::isOwner)) {
            SpaceMember successor = remaining.stream()
                    .min(Comparator.comparing(SpaceMember::joinedAt))
                    .orElseThrow();
            spaceRepository.saveMember(successor.promoteToOwner());
            pushPort.send(new PushMessage(
                    successor.userId(),
                    "OWNER_CHANGED",
                    space.name(),
                    "그룹의 새 방장이 되었어요.",
                    Map.of("spaceId", spaceId.toString())));
        }
        return true;
    }
}
