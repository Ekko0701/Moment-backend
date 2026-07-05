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
                .map(membership -> {
                    Space space = spaceRepository.findById(membership.spaceId())
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
                            space.type().name(),
                            space.status().name(),
                            space.createdAt().toEpochMilli(),
                            memberViews);
                })
                .toList();
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
                        "스페이스에속해있지않아요."));

        Instant now = clock.instant();

        SpaceMember left = myMember.leave(now);
        spaceRepository.saveMember(left);

        if (space.type().name().equals("ONE_TO_ONE")) {
            Space deactivated = space.deactivate();
            spaceRepository.save(deactivated);

            List<SpaceMember> remainingMembers = spaceRepository.findActiveMembersBySpaceId(spaceId);
            remainingMembers.stream()
                    .filter(m -> !m.userId().equals(meId))
                    .forEach(m -> pushPort.send(new PushMessage(
                            m.userId(),
                            "SPACE_DEACTIVATED",
                            "연결이 끝났어요",
                            "상대방이 연결을 종료했습니다.",
                            Map.of())));
        }
    }
}
