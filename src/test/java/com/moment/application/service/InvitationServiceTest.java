package com.moment.application.service;

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
                new MomentProperties.Auth(new MomentProperties.Auth.Apple("stub")),
                new MomentProperties.Invite(Duration.ofHours(24), Duration.ofHours(72)),
                new MomentProperties.Policy(true));
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
