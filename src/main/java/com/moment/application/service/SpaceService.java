package com.moment.application.service;

import com.moment.application.port.in.SpaceUseCase;
import com.moment.application.port.out.GroupJoinRequestPort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.space.SpaceType;
import com.moment.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SpaceService implements SpaceUseCase {

    private final SpaceRepositoryPort spaceRepository;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final GroupMemberExit groupMemberExit;
    private final GroupJoinRequestPort joinRequestPort;
    private final Clock clock;

    public SpaceService(
            SpaceRepositoryPort spaceRepository,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            GroupMemberExit groupMemberExit,
            GroupJoinRequestPort joinRequestPort,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.groupMemberExit = groupMemberExit;
        this.joinRequestPort = joinRequestPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceView> getMySpaces(UUID meId) {
        List<SpaceMember> myMemberships = spaceRepository.findActiveMembershipsByUserId(meId);
        List<UUID> spaceIds = myMemberships.stream().map(SpaceMember::spaceId).toList();
        Map<UUID, Space> spaces = spaceRepository.findAllByIds(spaceIds).stream()
                .collect(Collectors.toMap(Space::id, Function.identity()));

        // 멤버·사용자·대기 요청 수를 스페이스 수와 무관하게 한 번씩만 조회한다
        Map<UUID, List<SpaceMember>> membersBySpace = spaceRepository.findActiveMembersBySpaceIds(spaceIds).stream()
                .collect(Collectors.groupingBy(SpaceMember::spaceId));
        Map<UUID, User> users = findUsers(membersBySpace.values().stream().flatMap(List::stream).toList());
        Map<UUID, Long> pendingCounts = joinRequestPort.countPendingBySpaceIds(myMemberships.stream()
                .filter(m -> m.isOwner() && spaces.containsKey(m.spaceId()) && spaces.get(m.spaceId()).isGroup())
                .map(SpaceMember::spaceId)
                .toList());

        return myMemberships.stream()
                .map(membership -> {
                    Space space = spaces.get(membership.spaceId());
                    if (space == null) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "스페이스를 찾을 수 없어요.");
                    }
                    return buildView(space, membership, membersBySpace.getOrDefault(space.id(), List.of()), users,
                            pendingCounts.getOrDefault(space.id(), 0L).intValue());
                })
                // 1:1을 맨 앞에 둔다. 첫 스페이스를 연결 상대로 읽는 클라이언트도 1:1을 보게 된다
                .sorted(Comparator.comparing((SpaceView v) -> !SpaceType.ONE_TO_ONE.name().equals(v.type()))
                        .thenComparingLong(SpaceView::connectedAt))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceView getMySpace(UUID meId, UUID spaceId) {
        SpaceMember membership = spaceRepository.findMember(spaceId, meId)
                .filter(SpaceMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "스페이스 멤버가 아닙니다."));
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "스페이스를 찾을 수 없어요."));
        List<SpaceMember> members = spaceRepository.findActiveMembersBySpaceId(space.id());
        int pending = membership.isOwner() && space.isGroup()
                ? joinRequestPort.countPendingBySpaceIds(List.of(space.id())).getOrDefault(space.id(), 0L).intValue()
                : 0;
        return buildView(space, membership, members, findUsers(members), pending);
    }

    private Map<UUID, User> findUsers(List<SpaceMember> members) {
        return userRepository.findAllByIds(members.stream().map(SpaceMember::userId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
    }

    private SpaceView buildView(Space space, SpaceMember myMembership, List<SpaceMember> members,
            Map<UUID, User> users, int pendingJoinRequestCount) {
        List<MemberView> memberViews = members.stream()
                .map(member -> {
                    User user = users.get(member.userId());
                    if (user == null) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요.");
                    }
                    return new MemberView(
                            user.id(),
                            user.handle(),
                            user.nickname(),
                            user.profileImageUrl(),
                            member.joinedAt().toEpochMilli());
                })
                .toList();

        return new SpaceView(
                space.id(),
                space.name(),
                space.type().name(),
                space.status().name(),
                space.createdAt().toEpochMilli(),
                memberViews,
                myMembership.role().name(),
                space.maxMembers(),
                myMembership.notificationsEnabled(),
                pendingJoinRequestCount);
    }

    @Override
    public void leave(UUID meId, UUID spaceId) {
        // 스페이스 행을 먼저 잠그고 읽어, 동시에 일어난 종료·이름 변경의 최신 상태를 본다
        Space space = spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "스페이스를 찾을 수 없어요."));

        if (space.isGroup()) {
            leaveGroup(meId, spaceId);
            return;
        }

        List<SpaceMember> members = spaceRepository.findActiveMembersBySpaceId(spaceId);
        members.stream()
                .filter(m -> m.userId().equals(meId) && m.isActive())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "스페이스에 속해있지 않아요."));

        Instant now = clock.instant();

        // 1:1은 한 명이 나가면 스페이스가 종료된다.
        // 양쪽 멤버십을 모두 종료(leftAt 기록)하고 스페이스를 비활성화해
        // 멤버십/스페이스 상태를 정합적으로 유지한다. 이렇게 하면 남은 상대도
        // 비활성 스페이스의 '활성 멤버'로 남지 않아 재연결이 가능해진다.
        for (SpaceMember member : members) {
            spaceRepository.saveMember(member.leave(now));
        }
        spaceRepository.save(space.deactivate());

        members.stream()
                .filter(m -> !m.userId().equals(meId))
                .forEach(m -> pushPort.send(new PushMessage(
                        m.userId(),
                        "SPACE_DEACTIVATED",
                        "연결이 끝났어요",
                        "상대방이 연결을 종료했습니다.",
                        Map.of())));
    }

    private void leaveGroup(UUID meId, UUID spaceId) {
        if (!groupMemberExit.exit(spaceId, meId, clock.instant())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "스페이스에 속해있지 않아요.");
        }
    }

    @Override
    public void rename(UUID meId, UUID spaceId, String name) {
        // 스페이스 행을 잠가 나가기·종료와 직렬화한다. 잠금 없이 쓰면 그사이 종료된 그룹을 ACTIVE로 덮어쓸 수 있다
        Space space = spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "스페이스를 찾을 수 없어요."));

        SpaceMember me = spaceRepository.findActiveMembersBySpaceId(spaceId).stream()
                .filter(m -> m.userId().equals(meId) && m.isActive())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "스페이스 멤버가 아닙니다."));

        String normalized = (name == null || name.isBlank()) ? null : name.trim();
        if (space.isGroup()) {
            if (!me.isOwner()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 할 수 있어요.");
            }
            if (normalized == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "그룹 이름을 1~10자로 입력해 주세요.");
            }
        }
        spaceRepository.save(space.rename(normalized));
    }
}
