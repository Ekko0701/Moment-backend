package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.in.GroupSpaceUseCase.CreatedGroup;
import com.moment.application.port.in.GroupSpaceUseCase.JoinRequestView;
import com.moment.application.port.in.GroupSpaceUseCase.MemberView;
import com.moment.application.port.in.GroupSpaceUseCase.PendingJoinRequestView;
import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.application.port.out.GroupJoinRequestPort;
import com.moment.application.port.out.InviteCodePort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import com.moment.domain.space.GroupInviteCode;
import com.moment.domain.space.GroupJoinRequest;
import com.moment.domain.space.JoinRequestStatus;
import com.moment.domain.space.MemberRole;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.space.SpaceStatus;
import com.moment.domain.space.SpaceType;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("GroupSpaceService")
@ExtendWith(MockitoExtension.class)
class GroupSpaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final int MAX = 6;
    private static final int MAX_GROUPS = 10;

    @Mock private SpaceRepositoryPort spaceRepository;
    @Mock private GroupInviteCodePort codePort;
    @Mock private GroupJoinRequestPort joinRequestPort;
    @Mock private InviteCodePort inviteCodePort;
    @Mock private UserRepositoryPort userRepository;
    @Mock private PushPort pushPort;

    private GroupSpaceService service;
    private UUID meId;

    @BeforeEach
    void setUp() {
        MomentProperties properties = new MomentProperties(
                new MomentProperties.Jwt("secret", Duration.ofMinutes(30), Duration.ofDays(30)),
                new MomentProperties.Auth(MomentProperties.Auth.Apple.stub()),
                new MomentProperties.Invite(Duration.ofHours(24), Duration.ofHours(72), Duration.ofDays(7)),
                new MomentProperties.Policy(true, MAX, MAX_GROUPS),
                MomentProperties.Storage.stub());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new GroupSpaceService(spaceRepository, codePort, joinRequestPort, userRepository, pushPort,
                new InviteCodeGenerator(inviteCodePort, codePort), new GroupJoinAttemptLimiter(clock), properties, clock);
        meId = UUID.randomUUID();
    }

    private static Space group() {
        return Space.group(UUID.randomUUID(), "주말 산책", MAX, NOW.minusSeconds(86_400));
    }

    private static SpaceMember member(Space space, UUID userId, MemberRole role, long joinedSecondsAgo) {
        return new SpaceMember(UUID.randomUUID(), space.id(), userId, role, NOW.minusSeconds(joinedSecondsAgo), null);
    }

    private static User user(UUID id, String nickname) {
        return new User(id, "h" + id.toString().substring(0, 6), nickname, null, AuthProvider.APPLE, null, null, null,
                true, null, UserStatus.ACTIVE, NOW, NOW);
    }

    private static GroupInviteCode code(Space space, Instant expiresAt) {
        return new GroupInviteCode("ABC123", space.id(), UUID.randomUUID(), expiresAt, NOW.minusSeconds(60));
    }

    // ---------- create

    @Test
    @DisplayName("생성: 이름이 비었거나 10자를 넘으면 INVALID_REQUEST이고 아무것도 저장하지 않는다")
    void create_invalidName_rejected() {
        assertThatThrownBy(() -> service.create(meId, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.create(meId, "열한글자이상의그룹이름"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(spaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성: 정원이 기록된 GROUP 스페이스, 방장 멤버, 7일짜리 6자리 코드를 만든다")
    void create_savesGroupOwnerAndCode() {
        when(codePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreatedGroup created = service.create(meId, " 주말 산책 ");

        ArgumentCaptor<Space> spaceCaptor = ArgumentCaptor.forClass(Space.class);
        verify(spaceRepository).save(spaceCaptor.capture());
        Space saved = spaceCaptor.getValue();
        assertThat(saved.type()).isEqualTo(SpaceType.GROUP);
        assertThat(saved.name()).isEqualTo("주말 산책");
        assertThat(saved.maxMembers()).isEqualTo(MAX);

        ArgumentCaptor<SpaceMember> memberCaptor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository).saveMember(memberCaptor.capture());
        assertThat(memberCaptor.getValue().userId()).isEqualTo(meId);
        assertThat(memberCaptor.getValue().role()).isEqualTo(MemberRole.OWNER);

        assertThat(created.spaceId()).isEqualTo(saved.id());
        assertThat(created.code()).hasSize(6);
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    // ---------- reissue

    @Test
    @DisplayName("재발급: 방장이 아니면 FORBIDDEN")
    void reissue_notOwner_forbidden() {
        Space space = group();
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.MEMBER, 100)));

        assertThatThrownBy(() -> service.reissueCode(meId, space.id()))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(codePort, never()).save(any());
    }

    @Test
    @DisplayName("재발급: 이전 코드를 먼저 지우고 새 코드를 저장한다")
    void reissue_owner_deletesThenSaves() {
        Space space = group();
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.OWNER, 100)));
        when(codePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reissueCode(meId, space.id());

        InOrder order = inOrder(codePort);
        order.verify(codePort).deleteBySpaceId(space.id());
        order.verify(codePort).save(any());
    }

    // ---------- group limit

    private void stubGroupCount(UUID userId, int groups) {
        List<Space> spaces = IntStream.range(0, groups).mapToObj(i -> group()).toList();
        when(spaceRepository.findActiveMembershipsByUserId(userId)).thenReturn(spaces.stream()
                .map(sp -> member(sp, userId, MemberRole.MEMBER, 100))
                .toList());
        when(spaceRepository.findAllByIds(any())).thenReturn(spaces);
    }

    @Test
    @DisplayName("생성: 이미 그룹 10개에 속해 있으면 CONFLICT이고 만들지 않는다")
    void create_groupLimit_conflict() {
        stubGroupCount(meId, MAX_GROUPS);

        assertThatThrownBy(() -> service.create(meId, "열한번째"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("그룹은 최대 10개까지 참여할 수 있어요.");
        verify(spaceRepository, never()).save(any());
    }

    // ---------- join request

    private Space stubValidCode() {
        Space space = group();
        when(codePort.findByCode("ABC123")).thenReturn(Optional.of(code(space, NOW.plusSeconds(60))));
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        return space;
    }

    @Test
    @DisplayName("참여 요청: 만료된 코드는 INVALID_REQUEST이고 요청을 만들지 않는다")
    void requestJoin_expiredCode_invalid() {
        Space space = group();
        when(codePort.findByCode("ABC123")).thenReturn(Optional.of(code(space, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.requestJoin(meId, "abc123"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(joinRequestPort, never()).save(any());
    }

    @Test
    @DisplayName("참여 요청: 틀린 코드를 5번 넣으면 올바른 코드도 TOO_MANY_REQUESTS로 막힌다")
    void requestJoin_fiveFailures_locks() {
        when(codePort.findByCode("WRONG1")).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.requestJoin(meId, "WRONG1"))
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        }

        assertThatThrownBy(() -> service.requestJoin(meId, "ABC123"))
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        verify(codePort, never()).findByCode("ABC123");

        // 다른 사용자는 영향을 받지 않는다
        when(codePort.findByCode("WRONG2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requestJoin(UUID.randomUUID(), "WRONG2"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("참여 요청: 종료된 그룹의 코드는 INVALID_REQUEST")
    void requestJoin_inactiveGroup_invalid() {
        Space space = group().deactivate();
        when(codePort.findByCode("ABC123")).thenReturn(Optional.of(code(space, NOW.plusSeconds(60))));
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));

        assertThatThrownBy(() -> service.requestJoin(meId, "ABC123"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("참여 요청: 이미 활성 멤버면 CONFLICT")
    void requestJoin_alreadyMember_conflict() {
        Space space = stubValidCode();
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.MEMBER, 100)));

        assertThatThrownBy(() -> service.requestJoin(meId, "ABC123"))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("참여 요청: 같은 그룹에 대기 중 요청이 있으면 새로 만들지 않고 그 요청을 돌려준다")
    void requestJoin_existingPending_returnsSame() {
        Space space = stubValidCode();
        GroupJoinRequest pending = GroupJoinRequest.request(UUID.randomUUID(), space.id(), meId, NOW.minusSeconds(30));
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.empty());
        when(joinRequestPort.findPending(space.id(), meId)).thenReturn(Optional.of(pending));

        JoinRequestView view = service.requestJoin(meId, "ABC123");

        assertThat(view.id()).isEqualTo(pending.id());
        verify(joinRequestPort, never()).save(any());
        verify(pushPort, never()).send(any());
    }

    @Test
    @DisplayName("참여 요청: 정원이 찼으면 CONFLICT이고 요청을 만들지 않는다")
    void requestJoin_full_conflict() {
        Space space = stubValidCode();
        List<SpaceMember> full = IntStream.range(0, MAX)
                .mapToObj(i -> member(space, UUID.randomUUID(), i == 0 ? MemberRole.OWNER : MemberRole.MEMBER, 100 - i))
                .toList();
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.empty());
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(full);

        assertThatThrownBy(() -> service.requestJoin(meId, "ABC123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("정원이 찼어요.");
        verify(joinRequestPort, never()).save(any());
    }

    @Test
    @DisplayName("참여 요청: 요청자가 이미 그룹 10개에 속해 있으면 CONFLICT")
    void requestJoin_groupLimit_conflict() {
        Space space = stubValidCode();
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.empty());
        when(spaceRepository.findActiveMembersBySpaceId(space.id()))
                .thenReturn(List.of(member(space, UUID.randomUUID(), MemberRole.OWNER, 300)));
        stubGroupCount(meId, MAX_GROUPS);

        assertThatThrownBy(() -> service.requestJoin(meId, "ABC123"))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(joinRequestPort, never()).save(any());
    }

    @Test
    @DisplayName("참여 요청: 멤버로 추가하지 않고 PENDING 요청만 만들며, 방장에게만 JOIN_REQUESTED를 보낸다")
    void requestJoin_createsPendingAndNotifiesOwner() {
        Space space = stubValidCode();
        UUID ownerId = UUID.randomUUID();
        SpaceMember owner = member(space, ownerId, MemberRole.OWNER, 300);
        SpaceMember other = member(space, UUID.randomUUID(), MemberRole.MEMBER, 200);
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.empty());
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(owner, other));
        when(joinRequestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(meId)).thenReturn(Optional.of(user(meId, "새친구")));

        JoinRequestView view = service.requestJoin(meId, " abc123 ");

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.spaceName()).isEqualTo("주말 산책");
        verify(spaceRepository, never()).saveMember(any());

        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushPort, times(1)).send(push.capture());
        assertThat(push.getValue().targetUserId()).isEqualTo(ownerId);
        assertThat(push.getValue().type()).isEqualTo("JOIN_REQUESTED");
    }

    // ---------- decide

    private GroupJoinRequest stubPendingRequest(Space space, UUID requesterId) {
        GroupJoinRequest request = GroupJoinRequest.request(UUID.randomUUID(), space.id(), requesterId, NOW.minusSeconds(60));
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.OWNER, 900)));
        when(joinRequestPort.findById(request.id())).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("수락: 방장이 아니면 FORBIDDEN")
    void decide_notOwner_forbidden() {
        Space space = group();
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.MEMBER, 100)));

        assertThatThrownBy(() -> service.decideJoinRequest(meId, space.id(), UUID.randomUUID(), true))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("수락: 요청자를 MEMBER로 추가하고 요청을 APPROVED로 바꾼다. 알림은 요청자와, 알림을 켠 다른 멤버에게 간다")
    void decide_approve_addsMemberAndNotifies() {
        Space space = group();
        UUID requesterId = UUID.randomUUID();
        GroupJoinRequest request = stubPendingRequest(space, requesterId);
        SpaceMember owner = member(space, meId, MemberRole.OWNER, 900);
        UUID otherId = UUID.randomUUID();
        SpaceMember other = member(space, otherId, MemberRole.MEMBER, 500);
        SpaceMember muted = member(space, UUID.randomUUID(), MemberRole.MEMBER, 400).withNotifications(false);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "새친구")));
        when(spaceRepository.findMember(space.id(), requesterId)).thenReturn(Optional.empty());
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(owner, other, muted));

        service.decideJoinRequest(meId, space.id(), request.id(), true);

        ArgumentCaptor<SpaceMember> memberCaptor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository).saveMember(memberCaptor.capture());
        assertThat(memberCaptor.getValue().userId()).isEqualTo(requesterId);
        assertThat(memberCaptor.getValue().role()).isEqualTo(MemberRole.MEMBER);

        ArgumentCaptor<GroupJoinRequest> requestCaptor = ArgumentCaptor.forClass(GroupJoinRequest.class);
        verify(joinRequestPort).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().status()).isEqualTo(JoinRequestStatus.APPROVED);

        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushPort, times(2)).send(push.capture());
        assertThat(push.getAllValues()).extracting(PushMessage::targetUserId).containsExactly(otherId, requesterId);
        assertThat(push.getAllValues()).extracting(PushMessage::type).containsExactly("MEMBER_JOINED", "JOIN_APPROVED");
    }

    @Test
    @DisplayName("수락: 나갔거나 내보내졌던 사람은 같은 멤버 행을 재활성화한다")
    void decide_approve_formerMember_reactivatesSameRow() {
        Space space = group();
        UUID requesterId = UUID.randomUUID();
        GroupJoinRequest request = stubPendingRequest(space, requesterId);
        SpaceMember left = member(space, requesterId, MemberRole.MEMBER, 500).leave(NOW.minusSeconds(100));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "돌아온")));
        when(spaceRepository.findMember(space.id(), requesterId)).thenReturn(Optional.of(left));
        when(spaceRepository.findActiveMembersBySpaceId(space.id()))
                .thenReturn(List.of(member(space, meId, MemberRole.OWNER, 900)));

        service.decideJoinRequest(meId, space.id(), request.id(), true);

        ArgumentCaptor<SpaceMember> captor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository).saveMember(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(left.id());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().joinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("수락: 정원이 찼으면 CONFLICT이고 멤버도 요청 상태도 바뀌지 않는다")
    void decide_approve_full_conflict() {
        Space space = group();
        UUID requesterId = UUID.randomUUID();
        GroupJoinRequest request = stubPendingRequest(space, requesterId);
        List<SpaceMember> full = IntStream.range(0, MAX)
                .mapToObj(i -> member(space, UUID.randomUUID(), MemberRole.MEMBER, 100 - i))
                .toList();
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "새친구")));
        when(spaceRepository.findMember(space.id(), requesterId)).thenReturn(Optional.empty());
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(full);

        assertThatThrownBy(() -> service.decideJoinRequest(meId, space.id(), request.id(), true))
                .hasMessage("정원이 찼어요.");
        verify(spaceRepository, never()).saveMember(any());
        verify(joinRequestPort, never()).save(any());
    }

    @Test
    @DisplayName("거절: 요청을 REJECTED로 바꾸고 요청자에게 JOIN_REJECTED를 보낸다. 멤버는 추가하지 않는다")
    void decide_reject_marksRejected() {
        Space space = group();
        UUID requesterId = UUID.randomUUID();
        GroupJoinRequest request = stubPendingRequest(space, requesterId);

        service.decideJoinRequest(meId, space.id(), request.id(), false);

        ArgumentCaptor<GroupJoinRequest> requestCaptor = ArgumentCaptor.forClass(GroupJoinRequest.class);
        verify(joinRequestPort).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().status()).isEqualTo(JoinRequestStatus.REJECTED);
        verify(spaceRepository, never()).saveMember(any());

        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushPort).send(push.capture());
        assertThat(push.getValue().targetUserId()).isEqualTo(requesterId);
        assertThat(push.getValue().type()).isEqualTo("JOIN_REJECTED");
    }

    @Test
    @DisplayName("수락·거절: 이미 처리된 요청이면 CONFLICT")
    void decide_alreadyDecided_conflict() {
        Space space = group();
        GroupJoinRequest canceled = GroupJoinRequest.request(UUID.randomUUID(), space.id(), UUID.randomUUID(), NOW)
                .cancel(NOW);
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.OWNER, 900)));
        when(joinRequestPort.findById(canceled.id())).thenReturn(Optional.of(canceled));

        assertThatThrownBy(() -> service.decideJoinRequest(meId, space.id(), canceled.id(), true))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> service.decideJoinRequest(meId, space.id(), canceled.id(), false))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(spaceRepository, never()).saveMember(any());
    }

    @Test
    @DisplayName("취소: 내 요청만 취소할 수 있고, 취소하면 CANCELED가 된다")
    void cancel_onlyOwnRequest() {
        Space space = group();
        GroupJoinRequest mine = GroupJoinRequest.request(UUID.randomUUID(), space.id(), meId, NOW);
        GroupJoinRequest others = GroupJoinRequest.request(UUID.randomUUID(), space.id(), UUID.randomUUID(), NOW);
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(joinRequestPort.findById(mine.id())).thenReturn(Optional.of(mine));
        when(joinRequestPort.findById(others.id())).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> service.cancelJoinRequest(meId, space.id(), others.id()))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        service.cancelJoinRequest(meId, space.id(), mine.id());
        ArgumentCaptor<GroupJoinRequest> captor = ArgumentCaptor.forClass(GroupJoinRequest.class);
        verify(joinRequestPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(JoinRequestStatus.CANCELED);
    }

    @Test
    @DisplayName("요청 목록: 방장에게 대기 중 요청을 보여주되 탈퇴한 사용자의 요청은 뺀다")
    void joinRequests_hidesWithdrawnUsers() {
        Space space = group();
        UUID activeId = UUID.randomUUID();
        UUID withdrawnId = UUID.randomUUID();
        when(spaceRepository.findById(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.OWNER, 900)));
        when(joinRequestPort.findPendingBySpaceId(space.id())).thenReturn(List.of(
                GroupJoinRequest.request(UUID.randomUUID(), space.id(), activeId, NOW),
                GroupJoinRequest.request(UUID.randomUUID(), space.id(), withdrawnId, NOW)));
        when(userRepository.findAllByIds(any())).thenReturn(List.of(
                user(activeId, "대기"), user(withdrawnId, "탈퇴").withdraw("del_00000000000", NOW)));

        List<PendingJoinRequestView> requests = service.joinRequests(meId, space.id());

        assertThat(requests).extracting(PendingJoinRequestView::userId).containsExactly(activeId);
    }

    // ---------- members

    @Test
    @DisplayName("멤버 목록: 방장이 먼저, 나머지는 가입 순. 멤버가 아니면 FORBIDDEN")
    void members_ordersOwnerFirst() {
        Space space = group();
        UUID ownerId = UUID.randomUUID();
        UUID earlyId = UUID.randomUUID();
        SpaceMember me = member(space, meId, MemberRole.MEMBER, 100);
        SpaceMember early = member(space, earlyId, MemberRole.MEMBER, 900);
        SpaceMember owner = member(space, ownerId, MemberRole.OWNER, 50);
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(me, early, owner));
        when(userRepository.findAllByIds(any()))
                .thenReturn(List.of(user(meId, "나"), user(earlyId, "먼저"), user(ownerId, "방장")));

        List<MemberView> members = service.members(meId, space.id());

        assertThat(members).extracting(MemberView::userId).containsExactly(ownerId, earlyId, meId);
        assertThat(members.get(0).role()).isEqualTo("OWNER");

        assertThatThrownBy(() -> service.members(UUID.randomUUID(), space.id()))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ---------- kick

    @Test
    @DisplayName("내보내기: 자기 자신은 INVALID_REQUEST")
    void kick_self_invalid() {
        assertThatThrownBy(() -> service.kick(meId, UUID.randomUUID(), meId))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("내보내기: 방장이 아니면 FORBIDDEN")
    void kick_notOwner_forbidden() {
        Space space = group();
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.MEMBER, 100)));

        assertThatThrownBy(() -> service.kick(meId, space.id(), UUID.randomUUID()))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("내보내기: 대상의 leftAt을 기록하고 대상에게 REMOVED_FROM_SPACE를 보낸다")
    void kick_owner_removesAndNotifies() {
        Space space = group();
        UUID targetId = UUID.randomUUID();
        SpaceMember target = member(space, targetId, MemberRole.MEMBER, 100);
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId))
                .thenReturn(Optional.of(member(space, meId, MemberRole.OWNER, 500)));
        when(spaceRepository.findMember(space.id(), targetId)).thenReturn(Optional.of(target));

        service.kick(meId, space.id(), targetId);

        ArgumentCaptor<SpaceMember> captor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository).saveMember(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(target.id());
        assertThat(captor.getValue().leftAt()).isEqualTo(NOW);

        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushPort).send(push.capture());
        assertThat(push.getValue().targetUserId()).isEqualTo(targetId);
        assertThat(push.getValue().type()).isEqualTo("REMOVED_FROM_SPACE");
    }

    @Test
    @DisplayName("1:1 스페이스에는 그룹 관리 작업을 할 수 없다")
    void kick_oneToOne_invalid() {
        Space oneToOne = Space.oneToOne(UUID.randomUUID(), NOW);
        when(spaceRepository.findByIdForUpdate(oneToOne.id())).thenReturn(Optional.of(oneToOne));

        assertThatThrownBy(() -> service.kick(meId, oneToOne.id(), UUID.randomUUID()))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ---------- notifications

    @Test
    @DisplayName("알림 끄기: 내 멤버십의 플래그만 바꾼다")
    void setNotifications_updatesMyMembership() {
        Space space = group();
        SpaceMember me = member(space, meId, MemberRole.MEMBER, 100);
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.of(me));

        service.setNotifications(meId, space.id(), false);

        // 내보내기·방장 승계와 직렬화되도록 스페이스 행을 먼저 잠근다
        InOrder order = inOrder(spaceRepository);
        order.verify(spaceRepository).findByIdForUpdate(space.id());
        order.verify(spaceRepository).findMember(space.id(), meId);

        ArgumentCaptor<SpaceMember> captor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(spaceRepository).saveMember(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(me.id());
        assertThat(captor.getValue().notificationsEnabled()).isFalse();
    }

    // ---------- delete

    @Test
    @DisplayName("그룹 삭제: 전원 leftAt, 스페이스 INACTIVE, 코드 삭제, 나 이외 멤버에게 SPACE_DEACTIVATED")
    void delete_owner_endsGroup() {
        Space space = group();
        UUID otherId = UUID.randomUUID();
        SpaceMember owner = member(space, meId, MemberRole.OWNER, 500);
        SpaceMember other = member(space, otherId, MemberRole.MEMBER, 100);
        when(spaceRepository.findByIdForUpdate(space.id())).thenReturn(Optional.of(space));
        when(spaceRepository.findMember(space.id(), meId)).thenReturn(Optional.of(owner));
        when(spaceRepository.findActiveMembersBySpaceId(space.id())).thenReturn(List.of(owner, other));

        service.delete(meId, space.id());

        verify(spaceRepository, times(2)).saveMember(any());
        ArgumentCaptor<Space> spaceCaptor = ArgumentCaptor.forClass(Space.class);
        verify(spaceRepository).save(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().status()).isEqualTo(SpaceStatus.INACTIVE);
        verify(codePort).deleteBySpaceId(space.id());

        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushPort).send(push.capture());
        assertThat(push.getValue().targetUserId()).isEqualTo(otherId);
        assertThat(push.getValue().type()).isEqualTo("SPACE_DEACTIVATED");
    }
}
