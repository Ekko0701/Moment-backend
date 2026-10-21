package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.in.InvitationUseCase.RespondCommand;
import com.moment.application.port.out.BlockPort;
import com.moment.application.port.out.InviteCodePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.SpaceInvitationPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import com.moment.domain.invite.InvitationStatus;
import com.moment.domain.invite.InvitationVia;
import com.moment.domain.invite.SpaceInvitation;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.user.AuthProvider;
import com.moment.domain.user.User;
import com.moment.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("InvitationService")
@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Mock private SpaceRepositoryPort spaceRepository;
    @Mock private InviteCodePort inviteCodePort;
    @Mock private SpaceInvitationPort invitationPort;
    @Mock private BlockPort blockPort;
    @Mock private UserRepositoryPort userRepository;
    @Mock private PushPort pushPort;

    private InvitationService service;
    private UUID meId;
    private UUID fromUserId;

    @BeforeEach
    void setUp() {
        MomentProperties properties = new MomentProperties(
                new MomentProperties.Jwt("secret", Duration.ofMinutes(30), Duration.ofDays(30)),
                new MomentProperties.Auth(MomentProperties.Auth.Apple.stub()),
                new MomentProperties.Invite(Duration.ofHours(24), Duration.ofHours(72), Duration.ofDays(7)),
                new MomentProperties.Policy(true, 6),
                MomentProperties.Storage.stub());
        service = new InvitationService(spaceRepository, inviteCodePort, invitationPort, blockPort,
                userRepository, pushPort, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        meId = UUID.randomUUID();
        fromUserId = UUID.randomUUID();
    }

    private User activeUser(UUID id) {
        return new User(id, "handle", "닉", null, AuthProvider.APPLE, null, null, null, true,
                null, UserStatus.ACTIVE, NOW, NOW);
    }

    private SpaceInvitation pendingInvitation() {
        return new SpaceInvitation(UUID.randomUUID(), fromUserId, meId, InvitationVia.CODE,
                InvitationStatus.PENDING, NOW, null);
    }

    @Test
    @DisplayName("수락: 발신자가 이미 활성 스페이스를 가지면 CONFLICT")
    void accept_senderAlreadyConnected_conflict() {
        SpaceInvitation invitation = pendingInvitation();
        when(invitationPort.findByIdForUpdate(invitation.id())).thenReturn(Optional.of(invitation));
        when(userRepository.findById(meId)).thenReturn(Optional.of(activeUser(meId)));
        when(userRepository.findById(fromUserId)).thenReturn(Optional.of(activeUser(fromUserId)));
        when(blockPort.existsBlockBetween(meId, fromUserId)).thenReturn(false);
        when(spaceRepository.existsActiveOneToOneMembership(meId)).thenReturn(false);
        when(spaceRepository.existsActiveOneToOneMembership(fromUserId)).thenReturn(true);

        assertThatThrownBy(() -> service.respond(meId, invitation.id(), new RespondCommand("accept")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("TTL 경과: 만료된 PENDING 초대는 저장 없이 거부된다 (저장하면 예외 rollback으로 취소됨)")
    void accept_expiredPending_rejectsWithoutSave() {
        // 생성 후 73시간 경과 (TTL 72h 초과)
        SpaceInvitation stale = new SpaceInvitation(UUID.randomUUID(), fromUserId, meId,
                InvitationVia.CODE, InvitationStatus.PENDING, NOW.minus(Duration.ofHours(73)), null);
        when(invitationPort.findByIdForUpdate(stale.id())).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> service.respond(meId, stale.id(), new RespondCommand("accept")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        // 예외로 트랜잭션이 rollback되므로 상태 저장에 의존하지 않는다 — 저장 자체가 없어야 한다.
        // 재초대 가능 여부는 existsPendingBetween의 TTL 조건이 보장한다.
        verify(invitationPort, never()).save(any(SpaceInvitation.class));
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("TTL이 지난 PENDING이 남아 있어도 새 초대 발송을 막지 않는다 (조회에 TTL 조건)")
    void send_stalePendingDoesNotBlock() {
        UUID targetId = UUID.randomUUID();
        // TTL 조건이 포함된 조회가 false를 반환하는 상황 (stale PENDING만 존재)
        when(spaceRepository.existsActiveOneToOneMembership(meId)).thenReturn(false);
        when(blockPort.existsBlockBetween(meId, targetId)).thenReturn(false);
        when(invitationPort.existsPendingBetween(
                org.mockito.ArgumentMatchers.eq(meId),
                org.mockito.ArgumentMatchers.eq(targetId),
                any(Instant.class))).thenReturn(false);
        when(invitationPort.existsDeclinedBetweenSince(
                org.mockito.ArgumentMatchers.eq(meId),
                org.mockito.ArgumentMatchers.eq(targetId),
                any(Instant.class))).thenReturn(false);
        when(invitationPort.countByFromUserIdSince(
                org.mockito.ArgumentMatchers.eq(meId), any(Instant.class))).thenReturn(0);

        service.send(meId, new com.moment.application.port.in.InvitationUseCase.SendCommand(targetId, null));

        // TTL 파라미터가 전달된 조회로 판정하고, 새 초대가 저장된다
        verify(invitationPort).existsPendingBetween(
                org.mockito.ArgumentMatchers.eq(meId),
                org.mockito.ArgumentMatchers.eq(targetId),
                any(Instant.class));
        verify(invitationPort).save(any(SpaceInvitation.class));
    }

    @Test
    @DisplayName("수락 성공: 스페이스와 멤버 2명을 생성하고 초대를 수락 처리한다")
    void accept_happyPath_createsSpaceAndMembers() {
        SpaceInvitation invitation = pendingInvitation();
        when(invitationPort.findByIdForUpdate(invitation.id())).thenReturn(Optional.of(invitation));
        when(userRepository.findById(meId)).thenReturn(Optional.of(activeUser(meId)));
        when(userRepository.findById(fromUserId)).thenReturn(Optional.of(activeUser(fromUserId)));
        when(blockPort.existsBlockBetween(meId, fromUserId)).thenReturn(false);
        when(spaceRepository.existsActiveOneToOneMembership(meId)).thenReturn(false);
        when(spaceRepository.existsActiveOneToOneMembership(fromUserId)).thenReturn(false);
        when(invitationPort.findAllPendingByUserId(any())).thenReturn(List.of());

        service.respond(meId, invitation.id(), new RespondCommand("accept"));

        verify(spaceRepository).save(any(Space.class));
        verify(spaceRepository, times(2)).saveMember(any(SpaceMember.class));
        verify(invitationPort).save(any(SpaceInvitation.class));
        verify(pushPort).send(any(PushPort.PushMessage.class));
    }
}
