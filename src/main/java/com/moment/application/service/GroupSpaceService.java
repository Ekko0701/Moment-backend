package com.moment.application.service;

import com.moment.application.port.in.GroupSpaceUseCase;
import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.application.port.out.GroupJoinRequestPort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import com.moment.domain.space.GroupInviteCode;
import com.moment.domain.space.GroupJoinRequest;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 스페이스 서비스.
 *
 * <p>초대 코드를 입력하면 참여 요청이 생기고, 방장이 수락해야 멤버가 된다.
 * 멤버 수나 요청 상태를 바꾸는 작업(요청·수락·거절·취소·내보내기·종료)은 스페이스 행을 비관적 잠금으로 먼저 잡아 직렬화한다.
 * 그래서 동시에 여러 요청을 수락해도 정원을 넘지 않고, 같은 요청을 수락과 취소가 동시에 바꾸지 못한다.
 *
 * <p>사용자당 그룹 수 상한은 해당 사용자의 행을 잠그지 않고 센다. 서로 다른 그룹에서 같은 사람을 동시에 수락하면
 * 상한을 하나 넘길 수 있지만, 탈퇴(사용자 행 → 스페이스 행 순서)와 잠금 순서가 엇갈려 교착되는 것을 피하기 위한 선택이다.
 */
@Service
@Transactional
public class GroupSpaceService implements GroupSpaceUseCase {

    private static final int NAME_MAX_LENGTH = 10;
    private static final String INVALID_CODE_MESSAGE = "유효하지 않은 코드예요. 그룹 방장에게 새 코드를 요청해 주세요.";
    private static final String REQUEST_NOT_FOUND_MESSAGE = "참여 요청을 찾을 수 없어요.";

    private final SpaceRepositoryPort spaceRepository;
    private final GroupInviteCodePort codePort;
    private final GroupJoinRequestPort joinRequestPort;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final InviteCodeGenerator codeGenerator;
    private final GroupJoinAttemptLimiter attemptLimiter;
    private final MomentProperties properties;
    private final Clock clock;

    public GroupSpaceService(
            SpaceRepositoryPort spaceRepository,
            GroupInviteCodePort codePort,
            GroupJoinRequestPort joinRequestPort,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            InviteCodeGenerator codeGenerator,
            GroupJoinAttemptLimiter attemptLimiter,
            MomentProperties properties,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.codePort = codePort;
        this.joinRequestPort = joinRequestPort;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.codeGenerator = codeGenerator;
        this.attemptLimiter = attemptLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public CreatedGroup create(UUID meId, String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "그룹 이름을 1~10자로 입력해 주세요.");
        }
        if (hasNoGroupSlot(meId)) {
            throw new BusinessException(ErrorCode.CONFLICT, groupLimitMessage());
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
        Space space = lockActiveGroup(spaceId);
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
    public JoinRequestView requestJoin(UUID meId, String rawCode) {
        attemptLimiter.ensureAllowed(meId);
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        Instant now = clock.instant();

        Optional<GroupInviteCode> code = codePort.findByCode(normalized).filter(c -> !c.isExpired(now));
        if (code.isEmpty()) {
            attemptLimiter.recordFailure(meId);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, INVALID_CODE_MESSAGE);
        }

        Space space = spaceRepository.findByIdForUpdate(code.get().spaceId())
                .filter(Space::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, INVALID_CODE_MESSAGE));

        if (spaceRepository.findMember(space.id(), meId).filter(SpaceMember::isActive).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 참여 중인 그룹이에요.");
        }

        Optional<GroupJoinRequest> pending = joinRequestPort.findPending(space.id(), meId);
        if (pending.isPresent()) {
            return toView(pending.get(), space);
        }

        List<SpaceMember> activeMembers = spaceRepository.findActiveMembersBySpaceId(space.id());
        if (activeMembers.size() >= space.maxMembers()) {
            throw new BusinessException(ErrorCode.CONFLICT, "정원이 찼어요.");
        }
        if (hasNoGroupSlot(meId)) {
            throw new BusinessException(ErrorCode.CONFLICT, groupLimitMessage());
        }

        GroupJoinRequest request = joinRequestPort.save(GroupJoinRequest.request(UUID.randomUUID(), space.id(), meId, now));

        String nickname = userRepository.findById(meId).map(User::nickname).orElse("새 멤버");
        activeMembers.stream()
                .filter(SpaceMember::isOwner)
                .forEach(owner -> pushPort.send(new PushMessage(
                        owner.userId(),
                        "JOIN_REQUESTED",
                        space.name(),
                        nickname + "님이 그룹에 들어오고 싶어 해요.",
                        Map.of("spaceId", space.id().toString(), "requestId", request.id().toString()))));

        return toView(request, space);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequestView> myJoinRequests(UUID meId) {
        List<GroupJoinRequest> pending = joinRequestPort.findPendingByUserId(meId);
        Map<UUID, Space> spaces = spaceRepository.findAllByIds(pending.stream().map(GroupJoinRequest::spaceId).toList())
                .stream()
                .collect(Collectors.toMap(Space::id, Function.identity()));

        return pending.stream()
                .filter(r -> spaces.containsKey(r.spaceId()) && spaces.get(r.spaceId()).isActive())
                .map(r -> toView(r, spaces.get(r.spaceId())))
                .toList();
    }

    @Override
    public void cancelJoinRequest(UUID meId, UUID spaceId, UUID requestId) {
        spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, REQUEST_NOT_FOUND_MESSAGE));
        GroupJoinRequest request = joinRequestPort.findById(requestId)
                .filter(r -> r.spaceId().equals(spaceId) && r.userId().equals(meId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, REQUEST_NOT_FOUND_MESSAGE));
        joinRequestPort.save(request.cancel(clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingJoinRequestView> joinRequests(UUID meId, UUID spaceId) {
        Space space = activeGroup(spaceId);
        requireOwner(space.id(), meId);

        List<GroupJoinRequest> pending = joinRequestPort.findPendingBySpaceId(space.id());
        Map<UUID, User> users = userRepository.findAllByIds(pending.stream().map(GroupJoinRequest::userId).toList())
                .stream()
                .collect(Collectors.toMap(User::id, Function.identity()));

        // 탈퇴한 사용자의 요청은 보여주지 않는다
        return pending.stream()
                .filter(r -> users.containsKey(r.userId()) && users.get(r.userId()).isActive())
                .map(r -> {
                    User user = users.get(r.userId());
                    return new PendingJoinRequestView(r.id(), user.id(), user.handle(), user.nickname(),
                            user.profileImageUrl(), r.createdAt().toEpochMilli());
                })
                .toList();
    }

    @Override
    public void decideJoinRequest(UUID meId, UUID spaceId, UUID requestId, boolean approve) {
        Space space = lockActiveGroup(spaceId);
        requireOwner(space.id(), meId);

        GroupJoinRequest request = joinRequestPort.findById(requestId)
                .filter(r -> r.spaceId().equals(space.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, REQUEST_NOT_FOUND_MESSAGE));
        Instant now = clock.instant();

        if (!approve) {
            joinRequestPort.save(request.reject(now));
            pushPort.send(new PushMessage(
                    request.userId(),
                    "JOIN_REJECTED",
                    space.name(),
                    "참여 요청이 수락되지 않았어요.",
                    Map.of("spaceId", space.id().toString())));
            return;
        }

        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 요청이에요.");
        }
        User requester = userRepository.findById(request.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "요청한 사용자를 찾을 수 없어요."));

        Optional<SpaceMember> existing = spaceRepository.findMember(space.id(), requester.id());
        if (existing.filter(SpaceMember::isActive).isPresent()) {
            joinRequestPort.save(request.approve(now));
            return;
        }

        List<SpaceMember> activeMembers = spaceRepository.findActiveMembersBySpaceId(space.id());
        if (activeMembers.size() >= space.maxMembers()) {
            throw new BusinessException(ErrorCode.CONFLICT, "정원이 찼어요.");
        }
        if (hasNoGroupSlot(requester.id())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "요청한 사람이 이미 그룹 " + properties.policy().groupMaxPerUser() + "개에 참여하고 있어요.");
        }

        SpaceMember joined = existing
                .map(left -> left.rejoin(now))
                .orElseGet(() -> new SpaceMember(UUID.randomUUID(), space.id(), requester.id(), MemberRole.MEMBER, now, null));
        spaceRepository.saveMember(joined);
        joinRequestPort.save(request.approve(now));

        activeMembers.stream()
                .filter(m -> !m.userId().equals(meId) && m.notificationsEnabled())
                .forEach(m -> pushPort.send(new PushMessage(
                        m.userId(),
                        "MEMBER_JOINED",
                        space.name(),
                        requester.nickname() + "님이 그룹에 들어왔어요.",
                        Map.of("spaceId", space.id().toString()))));
        pushPort.send(new PushMessage(
                requester.id(),
                "JOIN_APPROVED",
                space.name(),
                "참여 요청이 수락됐어요. 지금 그룹을 확인해 보세요.",
                Map.of("spaceId", space.id().toString())));
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

    // 스페이스 행을 잠가 내보내기·방장 승계와 직렬화한다. 멤버 행 전체를 다시 쓰므로 잠금 없이 쓰면 그사이의 변경을 덮어쓴다.
    @Override
    public void setNotifications(UUID meId, UUID spaceId, boolean enabled) {
        spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "스페이스를 찾을 수 없어요."));
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

    /** 사용자가 이미 상한만큼 그룹에 속해 있으면 true. 만든 그룹과 참여한 그룹을 합쳐 센다. */
    private boolean hasNoGroupSlot(UUID userId) {
        List<UUID> spaceIds = spaceRepository.findActiveMembershipsByUserId(userId).stream()
                .map(SpaceMember::spaceId)
                .toList();
        long groups = spaceRepository.findAllByIds(spaceIds).stream()
                .filter(s -> s.isGroup() && s.isActive())
                .count();
        return groups >= properties.policy().groupMaxPerUser();
    }

    private String groupLimitMessage() {
        return "그룹은 최대 " + properties.policy().groupMaxPerUser() + "개까지 참여할 수 있어요.";
    }

    private static JoinRequestView toView(GroupJoinRequest request, Space space) {
        return new JoinRequestView(request.id(), space.id(), space.name(), request.status().name(),
                request.createdAt().toEpochMilli());
    }

    private Space activeGroup(UUID spaceId) {
        return requireActiveGroup(spaceRepository.findById(spaceId));
    }

    private Space lockActiveGroup(UUID spaceId) {
        return requireActiveGroup(spaceRepository.findByIdForUpdate(spaceId));
    }

    private static Space requireActiveGroup(Optional<Space> found) {
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
