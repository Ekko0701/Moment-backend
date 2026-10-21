package com.moment.application.service;

import com.moment.application.port.in.GroupSpaceUseCase;
import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import com.moment.domain.space.GroupInviteCode;
import com.moment.domain.space.MemberRole;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 스페이스 서비스.
 *
 * <p>멤버 수를 바꾸는 작업(참여·내보내기·삭제)은 스페이스 행을 비관적 잠금으로 먼저 잡아 직렬화한다.
 * 그래서 동시에 여러 명이 같은 코드로 들어와도 정원을 넘지 않는다.
 */
@Service
@Transactional
public class GroupSpaceService implements GroupSpaceUseCase {

    private static final int NAME_MAX_LENGTH = 10;
    private static final String INVALID_CODE_MESSAGE = "유효하지 않은 코드예요. 그룹 방장에게 새 코드를 요청해 주세요.";

    private final SpaceRepositoryPort spaceRepository;
    private final GroupInviteCodePort codePort;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final InviteCodeGenerator codeGenerator;
    private final MomentProperties properties;
    private final Clock clock;

    public GroupSpaceService(
            SpaceRepositoryPort spaceRepository,
            GroupInviteCodePort codePort,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            InviteCodeGenerator codeGenerator,
            MomentProperties properties,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.codePort = codePort;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public CreatedGroup create(UUID meId, String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "그룹 이름을 1~10자로 입력해 주세요.");
        }

        Instant now = clock.instant();
        Space space = Space.group(UUID.randomUUID(), normalized, properties.policy().groupMaxMembers(), now);
        spaceRepository.save(space);
        spaceRepository.saveMember(new SpaceMember(UUID.randomUUID(), space.id(), meId, MemberRole.OWNER, now, null));

        GroupInviteCode code = issue(space.id(), meId, now);
        return new CreatedGroup(space.id(), code.code(), code.expiresAt());
    }

    @Override
    public IssuedCode reissueCode(UUID meId, UUID spaceId) {
        Space space = activeGroup(spaceId);
        requireOwner(space.id(), meId);

        GroupInviteCode code = issue(space.id(), meId, clock.instant());
        return new IssuedCode(code.code(), code.expiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public IssuedCode currentCode(UUID meId, UUID spaceId) {
        Space space = activeGroup(spaceId);
        requireActiveMember(space.id(), meId);

        GroupInviteCode code = codePort.findBySpaceId(space.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "초대 코드가 없어요. 방장에게 발급을 요청해 주세요."));
        return new IssuedCode(code.code(), code.expiresAt());
    }

    @Override
    public UUID join(UUID meId, String rawCode) {
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        Instant now = clock.instant();

        GroupInviteCode code = codePort.findByCode(normalized)
                .filter(c -> !c.isExpired(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, INVALID_CODE_MESSAGE));

        Space space = spaceRepository.findByIdForUpdate(code.spaceId())
                .filter(Space::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, INVALID_CODE_MESSAGE));

        var existing = spaceRepository.findMember(space.id(), meId);
        if (existing.filter(SpaceMember::isActive).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 참여 중인 그룹이에요.");
        }

        List<SpaceMember> activeMembers = spaceRepository.findActiveMembersBySpaceId(space.id());
        if (activeMembers.size() >= space.maxMembers()) {
            throw new BusinessException(ErrorCode.CONFLICT, "정원이 찼어요.");
        }

        SpaceMember joined = existing
                .map(left -> left.rejoin(now))
                .orElseGet(() -> new SpaceMember(UUID.randomUUID(), space.id(), meId, MemberRole.MEMBER, now, null));
        spaceRepository.saveMember(joined);

        String nickname = userRepository.findById(meId).map(User::nickname).orElse("새 멤버");
        activeMembers.stream()
                .filter(SpaceMember::notificationsEnabled)
                .forEach(m -> pushPort.send(new PushMessage(
                        m.userId(),
                        "MEMBER_JOINED",
                        space.name(),
                        nickname + "님이 그룹에 들어왔어요.",
                        Map.of("spaceId", space.id().toString()))));

        return space.id();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID meId, UUID spaceId) {
        List<SpaceMember> members = spaceRepository.findActiveMembersBySpaceId(spaceId);
        if (members.stream().noneMatch(m -> m.userId().equals(meId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "스페이스 멤버가 아닙니다.");
        }

        Map<UUID, User> users = userRepository.findAllByIds(members.stream().map(SpaceMember::userId).toList())
                .stream()
                .collect(Collectors.toMap(User::id, Function.identity()));

        return members.stream()
                .sorted(Comparator.comparing((SpaceMember m) -> !m.isOwner()).thenComparing(SpaceMember::joinedAt))
                .map(m -> {
                    User user = users.get(m.userId());
                    if (user == null) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요.");
                    }
                    return new MemberView(user.id(), user.handle(), user.nickname(), user.profileImageUrl(),
                            m.role().name(), m.joinedAt().toEpochMilli());
                })
                .toList();
    }

    @Override
    public void kick(UUID meId, UUID spaceId, UUID targetUserId) {
        if (meId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "자기 자신은 내보낼 수 없어요. 나가기를 이용해 주세요.");
        }
        Space space = lockActiveGroup(spaceId);
        requireOwner(space.id(), meId);

        SpaceMember target = spaceRepository.findMember(space.id(), targetUserId)
                .filter(SpaceMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "그룹 멤버가 아니에요."));
        spaceRepository.saveMember(target.leave(clock.instant()));

        pushPort.send(new PushMessage(
                targetUserId,
                "REMOVED_FROM_SPACE",
                space.name(),
                "그룹에서 내보내졌어요.",
                Map.of("spaceId", space.id().toString())));
    }

    @Override
    public void setNotifications(UUID meId, UUID spaceId, boolean enabled) {
        SpaceMember me = spaceRepository.findMember(spaceId, meId)
                .filter(SpaceMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "스페이스에 속해있지 않아요."));
        spaceRepository.saveMember(me.withNotifications(enabled));
    }

    @Override
    public void delete(UUID meId, UUID spaceId) {
        Space space = lockActiveGroup(spaceId);
        requireOwner(space.id(), meId);

        Instant now = clock.instant();
        List<SpaceMember> members = spaceRepository.findActiveMembersBySpaceId(space.id());
        members.forEach(m -> spaceRepository.saveMember(m.leave(now)));
        spaceRepository.save(space.deactivate());
        codePort.deleteBySpaceId(space.id());

        members.stream()
                .filter(m -> !m.userId().equals(meId))
                .forEach(m -> pushPort.send(new PushMessage(
                        m.userId(),
                        "SPACE_DEACTIVATED",
                        space.name(),
                        "방장이 그룹을 종료했어요.",
                        Map.of("spaceId", space.id().toString()))));
    }

    private GroupInviteCode issue(UUID spaceId, UUID issuerId, Instant now) {
        codePort.deleteBySpaceId(spaceId);
        GroupInviteCode code = new GroupInviteCode(codeGenerator.generateUnique(), spaceId, issuerId,
                now.plus(properties.invite().groupCodeTtl()), now);
        return codePort.save(code);
    }

    private Space activeGroup(UUID spaceId) {
        return requireActiveGroup(spaceRepository.findById(spaceId));
    }

    private Space lockActiveGroup(UUID spaceId) {
        return requireActiveGroup(spaceRepository.findByIdForUpdate(spaceId));
    }

    private static Space requireActiveGroup(java.util.Optional<Space> found) {
        Space space = found.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "스페이스를 찾을 수 없어요."));
        if (!space.isGroup()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "그룹 스페이스가 아니에요.");
        }
        if (!space.isActive()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "종료된 그룹이에요.");
        }
        return space;
    }

    private SpaceMember requireActiveMember(UUID spaceId, UUID userId) {
        return spaceRepository.findMember(spaceId, userId)
                .filter(SpaceMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "스페이스 멤버가 아닙니다."));
    }

    private void requireOwner(UUID spaceId, UUID userId) {
        if (!requireActiveMember(spaceId, userId).isOwner()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 할 수 있어요.");
        }
    }
}
