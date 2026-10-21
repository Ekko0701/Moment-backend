package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.in.UserUseCase.UpdateMyProfileCommand;
import com.moment.application.port.out.AppleAuthPort;
import com.moment.application.port.out.AppleCredentialPort;
import com.moment.application.port.out.DevicePort;
import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.RefreshTokenPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.space.MemberRole;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.space.SpaceStatus;
import com.moment.domain.user.User;
import com.moment.domain.user.UserStatus;
import java.time.Clock;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private AppleAuthPort appleAuthPort;
    @Mock
    private AppleCredentialPort appleCredentialPort;
    @Mock
    private RefreshTokenPort refreshTokenPort;
    @Mock
    private DevicePort devicePort;
    @Mock
    private SpaceRepositoryPort spaceRepository;
    @Mock
    private PushPort pushPort;
    @Mock
    private GroupInviteCodePort groupInviteCodePort;

    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, appleAuthPort, appleCredentialPort, refreshTokenPort,
                devicePort, spaceRepository, pushPort,
                new GroupMemberExit(spaceRepository, groupInviteCodePort, pushPort),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.signUp(UUID.randomUUID(), "dongjoo_1234", "동주", "sub-1", NOW);
    }

    @Test
    @DisplayName("이미 사용 중인 핸들로 변경하면 CONFLICT를 던진다")
    void updateMe_duplicateHandle_throws() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(userRepository.existsByHandle("taken_handle")).thenReturn(true);

        var command = new UpdateMyProfileCommand(null, null, "taken_handle", null);

        assertThatThrownBy(() -> userService.updateMe(user.id(), command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("부분 업데이트 — 전달된 필드만 변경된다")
    void updateMe_partialUpdate() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateMe(user.id(),
                new UpdateMyProfileCommand("새닉네임", null, null, null));

        assertThat(result.nickname()).isEqualTo("새닉네임");
        assertThat(result.handle()).isEqualTo("dongjoo_1234");
        assertThat(result.searchable()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 시 익명화 저장과 리프레시 토큰 폐기가 수행된다")
    void deleteMe_withdrawsAndClearsTokens() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appleCredentialPort.findEncryptedRefreshToken(user.id()))
                .thenReturn(Optional.of("encrypted-refresh"));

        userService.deleteMe(user.id());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(UserStatus.DELETED);
        assertThat(captor.getValue().handle()).startsWith("del_");
        verify(refreshTokenPort).deleteAllByUserId(user.id());
        verify(appleAuthPort).revoke("encrypted-refresh");
        verify(appleCredentialPort).deleteByUserId(user.id());
    }

    @Test
    @DisplayName("방장이 탈퇴하면 그룹 행을 잠그고, 가장 먼저 들어온 남은 멤버가 방장이 된다")
    void deleteMe_groupOwner_promotesEarliestMember() {
        Space group = Space.group(UUID.randomUUID(), "주말 산책", 6, NOW.minusSeconds(86_400));
        SpaceMember mine = new SpaceMember(UUID.randomUUID(), group.id(), user.id(), MemberRole.OWNER, NOW.minusSeconds(900), null);
        SpaceMember earliest = new SpaceMember(UUID.randomUUID(), group.id(), UUID.randomUUID(), MemberRole.MEMBER, NOW.minusSeconds(800), null);
        SpaceMember later = new SpaceMember(UUID.randomUUID(), group.id(), UUID.randomUUID(), MemberRole.MEMBER, NOW.minusSeconds(100), null);
        stubWithdrawableUser();
        when(spaceRepository.findActiveMembershipsByUserId(user.id())).thenReturn(List.of(mine));
        when(spaceRepository.findById(group.id())).thenReturn(Optional.of(group));
        when(spaceRepository.findByIdForUpdate(group.id())).thenReturn(Optional.of(group));
        when(spaceRepository.findMember(group.id(), user.id())).thenReturn(Optional.of(mine));
        when(spaceRepository.findActiveMembersBySpaceId(group.id())).thenReturn(List.of(later, earliest));

        userService.deleteMe(user.id());

        verify(spaceRepository).findByIdForUpdate(group.id());
        ArgumentCaptor<SpaceMember> saved = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository, times(2)).saveMember(saved.capture());
        assertThat(saved.getAllValues().get(0).userId()).isEqualTo(user.id());
        assertThat(saved.getAllValues().get(0).isActive()).isFalse();
        assertThat(saved.getAllValues().get(1).userId()).isEqualTo(earliest.userId());
        assertThat(saved.getAllValues().get(1).isOwner()).isTrue();
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("그룹의 마지막 멤버가 탈퇴하면 그룹을 종료하고 초대 코드를 지운다")
    void deleteMe_lastGroupMember_deactivatesGroup() {
        Space group = Space.group(UUID.randomUUID(), "주말 산책", 6, NOW.minusSeconds(86_400));
        SpaceMember mine = new SpaceMember(UUID.randomUUID(), group.id(), user.id(), MemberRole.MEMBER, NOW.minusSeconds(900), null);
        stubWithdrawableUser();
        when(spaceRepository.findActiveMembershipsByUserId(user.id())).thenReturn(List.of(mine));
        when(spaceRepository.findById(group.id())).thenReturn(Optional.of(group));
        when(spaceRepository.findByIdForUpdate(group.id())).thenReturn(Optional.of(group));
        when(spaceRepository.findMember(group.id(), user.id())).thenReturn(Optional.of(mine));
        when(spaceRepository.findActiveMembersBySpaceId(group.id())).thenReturn(List.of());

        userService.deleteMe(user.id());

        ArgumentCaptor<Space> spaceCaptor = ArgumentCaptor.forClass(Space.class);
        verify(spaceRepository).save(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().status()).isEqualTo(SpaceStatus.INACTIVE);
        verify(groupInviteCodePort).deleteBySpaceId(group.id());
    }

    @Test
    @DisplayName("여러 그룹에 속한 사용자가 탈퇴하면 그룹 행을 ID 순서로 잠가 탈퇴끼리 교착되지 않게 한다")
    void deleteMe_multipleGroups_locksInIdOrder() {
        UUID lowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("7fffffff-0000-0000-0000-000000000001");
        Space low = Space.group(lowId, "가", 6, NOW);
        Space high = Space.group(highId, "나", 6, NOW);
        SpaceMember inHigh = new SpaceMember(UUID.randomUUID(), highId, user.id(), MemberRole.MEMBER, NOW, null);
        SpaceMember inLow = new SpaceMember(UUID.randomUUID(), lowId, user.id(), MemberRole.MEMBER, NOW, null);
        SpaceMember lowOwner = new SpaceMember(UUID.randomUUID(), lowId, UUID.randomUUID(), MemberRole.OWNER, NOW, null);
        SpaceMember highOwner = new SpaceMember(UUID.randomUUID(), highId, UUID.randomUUID(), MemberRole.OWNER, NOW, null);
        stubWithdrawableUser();
        when(spaceRepository.findActiveMembershipsByUserId(user.id())).thenReturn(List.of(inHigh, inLow));
        when(spaceRepository.findById(lowId)).thenReturn(Optional.of(low));
        when(spaceRepository.findById(highId)).thenReturn(Optional.of(high));
        when(spaceRepository.findByIdForUpdate(lowId)).thenReturn(Optional.of(low));
        when(spaceRepository.findByIdForUpdate(highId)).thenReturn(Optional.of(high));
        when(spaceRepository.findMember(lowId, user.id())).thenReturn(Optional.of(inLow));
        when(spaceRepository.findMember(highId, user.id())).thenReturn(Optional.of(inHigh));
        when(spaceRepository.findActiveMembersBySpaceId(lowId)).thenReturn(List.of(lowOwner));
        when(spaceRepository.findActiveMembersBySpaceId(highId)).thenReturn(List.of(highOwner));

        userService.deleteMe(user.id());

        InOrder order = inOrder(spaceRepository);
        order.verify(spaceRepository).findByIdForUpdate(lowId);
        order.verify(spaceRepository).findByIdForUpdate(highId);
    }

    private void stubWithdrawableUser() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appleCredentialPort.findEncryptedRefreshToken(user.id())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("탈퇴한 사용자 조회는 NOT_FOUND를 던진다")
    void getMe_deletedUser_throws() {
        User withdrawn = user.withdraw("del_abc12345678", NOW);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> userService.getMe(user.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
