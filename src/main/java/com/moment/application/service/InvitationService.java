package com.moment.application.service;

import com.moment.application.port.in.InvitationUseCase;
import com.moment.application.port.out.BlockPort;
import com.moment.application.port.out.InviteCodePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.SpaceInvitationPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import com.moment.domain.invite.InviteCode;
import com.moment.domain.invite.InvitationStatus;
import com.moment.domain.invite.InvitationVia;
import com.moment.domain.invite.SpaceInvitation;
import com.moment.domain.space.MemberRole;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvitationService implements InvitationUseCase {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int GENERATE_RETRY_LIMIT = 5;

    private final SpaceRepositoryPort spaceRepository;
    private final InviteCodePort inviteCodePort;
    private final SpaceInvitationPort invitationPort;
    private final BlockPort blockPort;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final MomentProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationService(
            SpaceRepositoryPort spaceRepository,
            InviteCodePort inviteCodePort,
            SpaceInvitationPort invitationPort,
            BlockPort blockPort,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            MomentProperties properties,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.inviteCodePort = inviteCodePort;
        this.invitationPort = invitationPort;
        this.blockPort = blockPort;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedCode issueCode(UUID meId) {
        inviteCodePort.deleteAllByIssuerUserId(meId);

        String code = generateUniqueCode();

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.invite().codeTtl());
        InviteCode inviteCode = new InviteCode(code, meId, expiresAt, null, now);
        inviteCodePort.save(inviteCode);

        return new IssuedCode(code, expiresAt);
    }

    @Override
    public SpaceInvitation send(UUID meId, SendCommand command) {
        if ((command.toUserId() == null && command.code() == null)
                || (command.toUserId() != null && command.code() != null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "대상 사용자 또는 초대 코드 중 하나만 제공해주세요.");
        }

        Instant now = clock.instant();
        UUID targetUserId;

        if (command.code() != null) {
            InviteCode invCode = inviteCodePort.findByCode(command.code())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                            "유효하지 않은 코드예요. 상대방에게 새 코드를 요청해 주세요."));

            if (invCode.isExpired(now)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "유효하지 않은 코드예요. 상대방에게 새 코드를 요청해 주세요.");
            }

            targetUserId = invCode.issuerUserId();
        } else {
            targetUserId = command.toUserId();
        }

        if (meId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "자신과는 연결할 수 없어요.");
        }

        if (blockPort.existsBlockBetween(meId, targetUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "해당 아이디의 사용자를 찾을 수 없어요.");
        }

        if (properties.policy().singleSpaceEnabled() && spaceRepository.existsActiveOneToOneMembership(meId)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 연결된 상대가 있어요.");
        }

        if (invitationPort.existsPendingBetween(meId, targetUserId)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 대기 중인 요청이 있어요.");
        }

        if (invitationPort.existsDeclinedBetweenSince(meId, targetUserId,
                now.minus(properties.invite().invitationTtl()))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.");
        }

        int recentCount = invitationPort.countByFromUserIdSince(meId,
                now.minus(Duration.ofHours(1)));
        if (recentCount >= 10) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.");
        }

        InvitationVia via = command.code() != null ? InvitationVia.CODE : InvitationVia.SEARCH;
        SpaceInvitation invitation = new SpaceInvitation(
                UUID.randomUUID(),
                meId,
                targetUserId,
                via,
                InvitationStatus.PENDING,
                now,
                null);
        invitationPort.save(invitation);

        pushPort.send(new PushMessage(
                targetUserId,
                "INVITATION_RECEIVED",
                "초대가 도착했어요",
                "새로운 연결 요청을 확인해보세요.",
                Map.of()));

        return invitation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceInvitation> list(UUID meId, boolean received) {
        List<SpaceInvitation> invitations = invitationPort.findAllByUser(meId, received);

        Instant now = clock.instant();
        invitations = invitations.stream()
                .map(inv -> {
                    if (inv.status() == InvitationStatus.PENDING
                            && inv.isExpiredBy(now, properties.invite().invitationTtl())) {
                        SpaceInvitation expired = inv.expire(now);
                        invitationPort.save(expired);
                        return expired;
                    }
                    return inv;
                })
                .toList();

        return invitations;
    }

    @Override
    public void respond(UUID meId, UUID invitationId, RespondCommand command) {
        // 행을 잠가(비관적 쓰기 잠금) 동시 수락 시 이중 스페이스 생성을 방지한다.
        SpaceInvitation invitation = invitationPort.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "대상을 찾을 수 없어요."));

        Instant now = clock.instant();
        if (invitation.status() == InvitationStatus.PENDING
                && invitation.isExpiredBy(now, properties.invite().invitationTtl())) {
            invitation = invitation.expire(now);
            invitationPort.save(invitation);
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "만료된 요청이에요.");
        }

        switch (command.action().toLowerCase()) {
            case "accept" -> handleAccept(meId, invitation, now);
            case "decline" -> handleDecline(meId, invitation, now);
            case "cancel" -> handleCancel(meId, invitation, now);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "올바르지 않은 액션입니다.");
        }
    }

    private void handleAccept(UUID meId, SpaceInvitation invitation, Instant now) {
        if (!invitation.toUserId().equals(meId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "이 초대를 수락할 수 없어요.");
        }

        // 이미 처리된(수락/거절/취소/만료) 초대는 다시 수락할 수 없다.
        if (invitation.status() != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 처리된 요청이에요.");
        }

        UUID fromUserId = invitation.fromUserId();

        // 두 참여자 행을 id 순서로 잠근다. 초대 행 잠금만으로는 서로 다른 초대(A→B, C→B)가
        // 같은 사용자 B에서 서로를 막지 못한다. 사용자 행을 잠가야 B를 공유하는 동시 수락이
        // 직렬화되어 1인 1스페이스 불변식이 유지된다. (쿼리가 id 순서로 잠가 데드락 회피)
        userRepository.lockAllByIdsForUpdate(List.of(meId, fromUserId));

        // 양쪽 계정이 모두 활성 상태여야 한다 (탈퇴/정지 계정과는 연결 불가).
        if (!isActiveUser(meId) || !isActiveUser(fromUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "연결할 수 없는 상대예요.");
        }

        // 수락 시점에 차단 관계를 다시 확인한다 (요청 이후 차단됐을 수 있음).
        if (blockPort.existsBlockBetween(meId, fromUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "연결할 수 없는 상대예요.");
        }

        // 1인 1스페이스 정책은 양쪽 모두에 적용한다.
        // (초대 발신자가 요청 후 다른 상대와 연결됐다면 이 수락으로 두 번째 스페이스가 생기면 안 됨)
        if (properties.policy().singleSpaceEnabled()
                && (spaceRepository.existsActiveOneToOneMembership(meId)
                        || spaceRepository.existsActiveOneToOneMembership(fromUserId))) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 연결된 상대가 있어요.");
        }

        Space space = Space.oneToOne(UUID.randomUUID(), now);
        spaceRepository.save(space);

        SpaceMember ownerMember = new SpaceMember(
                UUID.randomUUID(),
                space.id(),
                invitation.fromUserId(),
                MemberRole.OWNER,
                now,
                null);
        spaceRepository.saveMember(ownerMember);

        SpaceMember memberMember = new SpaceMember(
                UUID.randomUUID(),
                space.id(),
                invitation.toUserId(),
                MemberRole.MEMBER,
                now,
                null);
        spaceRepository.saveMember(memberMember);

        SpaceInvitation accepted = invitation.accept(now);
        invitationPort.save(accepted);

        List<SpaceInvitation> allPending = invitationPort.findAllPendingByUserId(invitation.fromUserId());
        allPending.stream()
                .filter(inv -> inv.status() == InvitationStatus.PENDING && !inv.id().equals(invitation.id()))
                .forEach(inv -> invitationPort.save(inv.expire(now)));

        allPending = invitationPort.findAllPendingByUserId(invitation.toUserId());
        allPending.stream()
                .filter(inv -> inv.status() == InvitationStatus.PENDING && !inv.id().equals(invitation.id()))
                .forEach(inv -> invitationPort.save(inv.expire(now)));

        pushPort.send(new PushMessage(
                invitation.fromUserId(),
                "INVITATION_ACCEPTED",
                "연결이 완료되었어요",
                "새로운 스페이스에서 순간들을 공유해보세요.",
                Map.of()));
    }

    private void handleDecline(UUID meId, SpaceInvitation invitation, Instant now) {
        if (!invitation.toUserId().equals(meId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "이 초대를 거절할 수 없어요.");
        }

        SpaceInvitation declined = invitation.decline(now);
        invitationPort.save(declined);
    }

    private void handleCancel(UUID meId, SpaceInvitation invitation, Instant now) {
        if (!invitation.fromUserId().equals(meId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "이 초대를 취소할 수 없어요.");
        }

        SpaceInvitation canceled = invitation.cancel(now);
        invitationPort.save(canceled);
    }

    private boolean isActiveUser(UUID userId) {
        return userRepository.findById(userId).map(user -> user.isActive()).orElse(false);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < GENERATE_RETRY_LIMIT; attempt++) {
            String code = generateRandomCode();
            if (inviteCodePort.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "초대 코드 생성에 실패했어요. 다시 시도해주세요.");
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
