package com.moment.application.service;

import com.moment.application.port.in.SpaceUseCase;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SpaceService implements SpaceUseCase {

    private final SpaceRepositoryPort spaceRepository;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final Clock clock;

    public SpaceService(
            SpaceRepositoryPort spaceRepository,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceView> getMySpaces(UUID meId) {
        List<SpaceMember> myMemberships = spaceRepository.findActiveMembershipsByUserId(meId);

        return myMemberships.stream()
                .map(membership -> buildView(membership.spaceId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceView getMySpace(UUID meId, UUID spaceId) {
        boolean isMember = spaceRepository.findActiveMembershipsByUserId(meId).stream()
                .anyMatch(m -> m.spaceId().equals(spaceId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "스페이스 멤버가 아닙니다.");
        }
        return buildView(spaceId);
    }

    private SpaceView buildView(UUID spaceId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "스페이스를 찾을 수 없어요."));

        List<SpaceMember> members = spaceRepository.findActiveMembersBySpaceId(space.id());
        List<MemberView> memberViews = members.stream()
                .map(member -> {
                    User user = userRepository.findById(member.userId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                                    "사용자를 찾을 수 없어요."));
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
                memberViews);
    }

    @Override
    public void leave(UUID meId, UUID spaceId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "스페이스를 찾을 수 없어요."));

        List<SpaceMember> members = spaceRepository.findActiveMembersBySpaceId(spaceId);
        SpaceMember myMember = members.stream()
                .filter(m -> m.userId().equals(meId) && m.isActive())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "스페이스에 속해있지 않아요."));

        Instant now = clock.instant();

        if (space.type() == SpaceType.ONE_TO_ONE) {
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
        } else {
            // 그룹 스페이스(향후): 나간 사람만 종료
            spaceRepository.saveMember(myMember.leave(now));
        }
    }

    @Override
    public void rename(UUID meId, UUID spaceId, String name) {
            Space space = spaceRepository.findById(spaceId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                                            "스페이스를 찾을 수 없어요."));

            boolean isMember = spaceRepository.findActiveMembersBySpaceId(spaceId).stream()
                            .anyMatch(m -> m.userId().equals(meId) && m.isActive());
            if (!isMember) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "스페이스 멤버가 아닙니다.");
            }
            String normalized = (name == null || name.isBlank()) ? null : name.trim();
            spaceRepository.save(space.rename(normalized));
    }
}
