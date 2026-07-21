package com.moment.application.service;

import com.moment.application.port.in.UserUseCase;
import com.moment.application.port.out.DevicePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.RefreshTokenPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.user.User;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceType;
import com.moment.domain.space.SpaceMember;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService implements UserUseCase {

    private final UserRepositoryPort userRepository;
    private final RefreshTokenPort refreshTokenPort;
    private final DevicePort devicePort;
    private final SpaceRepositoryPort spaceRepository;
    private final PushPort pushPort;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(UserRepositoryPort userRepository, RefreshTokenPort refreshTokenPort,
            DevicePort devicePort, SpaceRepositoryPort spaceRepository, PushPort pushPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenPort = refreshTokenPort;
        this.devicePort = devicePort;
        this.spaceRepository = spaceRepository;
        this.pushPort = pushPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public User getMe(UUID userId) {
        return findActiveUser(userId);
    }

    @Override
    public User updateMe(UUID userId, UpdateMyProfileCommand command) {
        User user = findActiveUser(userId);
        Instant now = clock.instant();
        if (command.nickname() != null) {
            user = user.changeNickname(command.nickname(), now);
        }
        if (command.profileImageUrl() != null) {
            user = user.changeProfileImage(command.profileImageUrl(), now);
        }
        if (command.handle() != null && !command.handle().equals(user.handle())) {
            if (userRepository.existsByHandle(command.handle())) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디예요.");
            }
            user = user.changeHandle(command.handle(), now);
        }
        if (command.searchable() != null) {
            user = user.changeSearchable(command.searchable(), now);
        }
        return userRepository.save(user);
    }

    @Override
    public void deleteMe(UUID userId) {
        User user = findActiveUser(userId);
        Instant now = clock.instant();

        userRepository.save(user.withdraw(anonymizedHandle(), now));
        refreshTokenPort.deleteAllByUserId(userId);
        devicePort.deleteAllByUserId(userId);

        // 활성 멤버십 모두 이탈 처리
        List<SpaceMember> memberships = spaceRepository.findActiveMembershipsByUserId(userId);
        for (SpaceMember membership : memberships) {
            SpaceMember left = membership.leave(now);
            spaceRepository.saveMember(left);

            // ONE_TO_ONE 스페이스이면 비활성화 + 남은 멤버에게 푸시
            Space space = spaceRepository.findById(membership.spaceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "스페이스를 찾을 수 없어요."));

            if (space.type() == SpaceType.ONE_TO_ONE) {
                Space deactivated = space.deactivate();
                spaceRepository.save(deactivated);

                // 남은 멤버십도 종료해 멤버십/스페이스 상태를 정합적으로 유지한다.
                // (상대가 비활성 스페이스의 활성 멤버로 남아 계속 접근하는 것을 막음 — 나가기/차단과 동일)
                List<SpaceMember> activeMembers = spaceRepository.findActiveMembersBySpaceId(space.id());
                for (SpaceMember activeMem : activeMembers) {
                    if (!activeMem.userId().equals(userId)) {
                        spaceRepository.saveMember(activeMem.leave(now));
                        pushPort.send(new PushMessage(
                                activeMem.userId(),
                                "SPACE_DEACTIVATED",
                                "대화가 종료되었습니다.",
                                null,
                                Map.of("spaceId", space.id().toString())));
                    }
                }
            }
        }
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요."));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요.");
        }
        return user;
    }

    /** 탈퇴 계정의 핸들을 해제하기 위한 익명 핸들 ("del_" + 11 hex = 15자). */
    private String anonymizedHandle() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return "del_" + HexFormat.of().formatHex(bytes).substring(0, 11);
    }
}
