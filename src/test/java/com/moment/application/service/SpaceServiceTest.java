package com.moment.application.service;

import com.moment.domain.user.UserStatus;
import com.moment.domain.user.User;
import com.moment.domain.user.AuthProvider;
import com.moment.application.port.in.SpaceUseCase.SpaceView;
import com.moment.application.port.out.PushPort.PushMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.space.MemberRole;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.space.SpaceStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("SpaceService")
@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Mock private SpaceRepositoryPort spaceRepository;
    @Mock private UserRepositoryPort userRepository;
    @Mock private PushPort pushPort;
    @Mock private GroupInviteCodePort groupInviteCodePort;

    private SpaceService service;

    @BeforeEach
    void setUp() {
        service = new SpaceService(spaceRepository, userRepository, pushPort, groupInviteCodePort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("1:1 스페이스 나가기: 양쪽 멤버십을 모두 종료하고 스페이스를 비활성화한다")
    void leave_oneToOne_terminatesBothMembersAndDeactivates() {
        UUID meId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        Space space = Space.oneToOne(UUID.randomUUID(), NOW.minusSeconds(3600));
        SpaceMember mine = new SpaceMember(UUID.randomUUID(), space.id(), meId,
                MemberRole.OWNER, NOW.minusSeconds(3600), null);
        SpaceMember partner = new SpaceMember(UUID.randomUUID(), space.id(), partnerId,
                MemberRole.MEMBER, NOW.minusSeconds(3600), null);

        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findActiveMembersBySpaceId(space.id()))
                .thenReturn(List.of(mine, partner));

        service.leave(meId, space.id());

        // 나간 사람과 상대방 모두 leftAt이 기록되어야 한다
        ArgumentCaptor<SpaceMember> memberCaptor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository, times(2)).saveMember(memberCaptor.capture());
        assertThat(memberCaptor.getAllValues())
                .allSatisfy(m -> assertThat(m.leftAt()).isEqualTo(NOW));

        // 스페이스는 비활성화되어야 한다
        ArgumentCaptor<Space> spaceCaptor = ArgumentCaptor.forClass(Space.class);
        verify(spaceRepository).save(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().status()).isEqualTo(SpaceStatus.INACTIVE);

        // 상대방에게 종료 푸시 1건
        verify(pushPort).send(any(PushPort.PushMessage.class));
    }

    // ---- 스페이스 이름 변경 ----

    @Test
    @DisplayName("이름 변경: 멤버가 요청하면 앞뒤 공백을 제거해 저장한다")
    void rename_member_savesTrimmedName() {
        UUID meId = UUID.randomUUID();
        Space space = Space.oneToOne(UUID.randomUUID(), NOW.minusSeconds(3600));
        SpaceMember mine = new SpaceMember(UUID.randomUUID(), space.id(), meId,
                MemberRole.OWNER, NOW.minusSeconds(3600), null);

        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(mine));

        service.rename(meId, space.id(), "  우리들의 공간  ");

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("우리들의 공간");
    }

    @Test
    @DisplayName("이름 변경: 공백만 입력하면 null로 저장해 '이름 없음'을 한 가지로 표현한다")
    void rename_blankName_savesNull() {
        UUID meId = UUID.randomUUID();
        Space space = Space.oneToOne(UUID.randomUUID(), NOW.minusSeconds(3600));
        SpaceMember mine = new SpaceMember(UUID.randomUUID(), space.id(), meId,
                MemberRole.OWNER, NOW.minusSeconds(3600), null);

        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(mine));

        service.rename(meId, space.id(), "   ");

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().name()).isNull();
    }

    @Test
    @DisplayName("이름 변경: 멤버가 아니면 FORBIDDEN — 스페이스 ID만 알면 남의 공간을 바꿀 수 있어선 안 된다")
    void rename_nonMember_throwsForbidden() {
        UUID meId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        Space space = Space.oneToOne(UUID.randomUUID(), NOW.minusSeconds(3600));
        SpaceMember mine = new SpaceMember(UUID.randomUUID(), space.id(), meId,
                MemberRole.OWNER, NOW.minusSeconds(3600), null);

        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(mine));

        assertThatThrownBy(() -> service.rename(strangerId, space.id(), "탈취 시도"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 권한 검사에 걸렸으면 저장은 시도조차 되지 않아야 한다
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("이름 변경: 나간 멤버(leftAt 기록됨)는 더 이상 변경할 수 없다")
    void rename_leftMember_throwsForbidden() {
        UUID meId = UUID.randomUUID();
        Space space = Space.oneToOne(UUID.randomUUID(), NOW.minusSeconds(3600));
        SpaceMember left = new SpaceMember(UUID.randomUUID(), space.id(), meId,
                MemberRole.OWNER, NOW.minusSeconds(3600), NOW.minusSeconds(60));

        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(left));

        assertThatThrownBy(() -> service.rename(meId, space.id(), "새 이름"))
                .isInstanceOf(BusinessException.class);

        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("이름 변경: 없는 스페이스면 NOT_FOUND")
    void rename_missingSpace_throwsNotFound() {
        UUID spaceId = UUID.randomUUID();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(UUID.randomUUID(), spaceId, "새 이름"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---------- 그룹

    private Space group() {
        return Space.group(UUID.randomUUID(), "주말 산책", 6, NOW.minusSeconds(86_400));
    }

    @Test
    @DisplayName("그룹 나가기: 방장이 나가면 가장 먼저 들어온 남은 멤버가 방장이 되고 OWNER_CHANGED를 받는다")
    void leaveGroup_owner_promotesEarliestMember() {
        UUID meId = UUID.randomUUID();
        UUID earliestId = UUID.randomUUID();
        UUID laterId = UUID.randomUUID();
        Space space = group();
        SpaceMember owner = new SpaceMember(UUID.randomUUID(), space.id(), meId, MemberRole.OWNER, NOW.minusSeconds(900), null);
        SpaceMember earliest = new SpaceMember(UUID.randomUUID(), space.id(), earliestId, MemberRole.MEMBER, NOW.minusSeconds(800), null);
        SpaceMember later = new SpaceMember(UUID.randomUUID(), space.id(), laterId, MemberRole.MEMBER, NOW.minusSeconds(100), null);
        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.of(owner));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(later, earliest));

        service.leave(meId, space.id());

        ArgumentCaptor<SpaceMember> saved = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository, times(2)).saveMember(saved.capture());
        assertThat(saved.getAllValues().get(0).userId()).isEqualTo(meId);
        assertThat(saved.getAllValues().get(0).isActive()).isFalse();
        assertThat(saved.getAllValues().get(1).userId()).isEqualTo(earliestId);
        assertThat(saved.getAllValues().get(1).isOwner()).isTrue();
        verify(spaceRepository, never()).save(any());

        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushPort).send(push.capture());
        assertThat(push.getValue().targetUserId()).isEqualTo(earliestId);
        assertThat(push.getValue().type()).isEqualTo("OWNER_CHANGED");
    }

    @Test
    @DisplayName("그룹 나가기: 일반 멤버가 나가면 본인만 종료되고 방장은 그대로다")
    void leaveGroup_member_onlyLeaves() {
        UUID meId = UUID.randomUUID();
        Space space = group();
        SpaceMember me = new SpaceMember(UUID.randomUUID(), space.id(), meId, MemberRole.MEMBER, NOW.minusSeconds(100), null);
        SpaceMember owner = new SpaceMember(UUID.randomUUID(), space.id(), UUID.randomUUID(), MemberRole.OWNER, NOW.minusSeconds(900), null);
        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.of(me));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(owner));

        service.leave(meId, space.id());

        verify(spaceRepository, times(1)).saveMember(any());
        verify(spaceRepository, never()).save(any());
        verify(pushPort, never()).send(any());
    }

    @Test
    @DisplayName("그룹 나가기: 마지막 멤버가 나가면 그룹을 비활성화하고 초대 코드를 지운다")
    void leaveGroup_lastMember_deactivates() {
        UUID meId = UUID.randomUUID();
        Space space = group();
        SpaceMember me = new SpaceMember(UUID.randomUUID(), space.id(), meId, MemberRole.OWNER, NOW.minusSeconds(100), null);
        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.of(me));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of());

        service.leave(meId, space.id());

        ArgumentCaptor<Space> spaceCaptor = ArgumentCaptor.forClass(Space.class);
        verify(spaceRepository).save(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().status()).isEqualTo(SpaceStatus.INACTIVE);
        verify(groupInviteCodePort).deleteBySpaceId(space.id());
    }

    @Test
    @DisplayName("그룹 이름 변경: 방장이 아니면 FORBIDDEN, 방장이 빈 이름을 넣으면 INVALID_REQUEST")
    void renameGroup_ownerOnlyAndRequired() {
        UUID meId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Space space = group();
        SpaceMember me = new SpaceMember(UUID.randomUUID(), space.id(), meId, MemberRole.MEMBER, NOW, null);
        SpaceMember owner = new SpaceMember(UUID.randomUUID(), space.id(), ownerId, MemberRole.OWNER, NOW, null);
        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(me, owner));

        assertThatThrownBy(() -> service.rename(meId, space.id(), "새 이름"))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.rename(ownerId, space.id(), "  "))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(spaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("내 스페이스 목록: 1:1이 그룹보다 먼저 오고, 내 역할과 알림 설정을 담는다")
    void getMySpaces_oneToOneFirstWithMyMembership() {
        UUID meId = UUID.randomUUID();
        Space olderGroup = Space.group(UUID.randomUUID(), "오래된 그룹", 6, NOW.minusSeconds(9_000));
        Space oneToOne = Space.oneToOne(UUID.randomUUID(), NOW.minusSeconds(100));
        SpaceMember inGroup = new SpaceMember(UUID.randomUUID(), olderGroup.id(), meId, MemberRole.OWNER, NOW, null)
                .withNotifications(false);
        SpaceMember inPair = new SpaceMember(UUID.randomUUID(), oneToOne.id(), meId, MemberRole.MEMBER, NOW, null);
        User me = new User(meId, "me", "나", null, AuthProvider.APPLE, null, null, null, true, null,
                UserStatus.ACTIVE, NOW, NOW);
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(inGroup, inPair));
        when(spaceRepository.findAllByIds(any())).thenReturn(List.of(olderGroup, oneToOne));
        when(spaceRepository.findActiveMembersBySpaceId(olderGroup.id())).thenReturn(List.of(inGroup));
        when(spaceRepository.findActiveMembersBySpaceId(oneToOne.id())).thenReturn(List.of(inPair));
        when(userRepository.findAllByIds(any())).thenReturn(List.of(me));

        List<SpaceView> views = service.getMySpaces(meId);

        assertThat(views).extracting(SpaceView::id).containsExactly(oneToOne.id(), olderGroup.id());
        assertThat(views.get(1).myRole()).isEqualTo("OWNER");
        assertThat(views.get(1).notificationsEnabled()).isFalse();
        assertThat(views.get(1).maxMembers()).isEqualTo(6);
    }
}
