package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.ArgumentCaptor;
import com.moment.domain.space.Space;
import java.util.Set;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.application.port.in.MomentUseCase.CreateMomentCommand;
import com.moment.application.port.out.BlockPort;
import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.MomentRepositoryPort.TimelinePage;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.ReactionPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.StoragePort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.moment.Moment;
import com.moment.domain.space.MemberRole;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.user.User;
import com.moment.domain.user.AuthProvider;
import com.moment.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("MomentService")
@ExtendWith(MockitoExtension.class)
class MomentServiceTest {

    @Mock private MomentRepositoryPort momentRepository;
    @Mock private ReactionPort reactionRepository;
    @Mock private StoragePort storagePort;
    @Mock private SpaceRepositoryPort spaceRepository;
    @Mock private UserRepositoryPort userRepository;
    @Mock private PushPort pushPort;
    @Mock private BlockPort blockPort;

    private MomentService service;
    private Clock clock;
    private UUID meId;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
        service = new MomentService(momentRepository, reactionRepository, storagePort,
                spaceRepository, userRepository, pushPort, blockPort, clock);
        meId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("멤버가 아니면 FORBIDDEN")
    void 비멤버_FORBIDDEN() {
        when(spaceRepository.findActiveMembershipsByUserId(meId))
                .thenReturn(List.of());

        assertThatThrownBy(() ->
                service.create(meId, new CreateMomentCommand(spaceId, "url", "text")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("모먼트 생성 시 팬아웃 푸시 2종 발송")
    void 팬아웃_푸시_2종() {
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();
        SpaceMember myMem = new SpaceMember(UUID.randomUUID(), spaceId, meId, MemberRole.OWNER, Instant.now(), null);
        SpaceMember mem1 = new SpaceMember(UUID.randomUUID(), spaceId, memberId1, MemberRole.MEMBER, Instant.now(), null);
        SpaceMember mem2 = new SpaceMember(UUID.randomUUID(), spaceId, memberId2, MemberRole.MEMBER, Instant.now(), null);

        when(spaceRepository.findActiveMembershipsByUserId(meId))
                .thenReturn(List.of(myMem));
        when(spaceRepository.findActiveMembersBySpaceId(spaceId))
                .thenReturn(List.of(myMem, mem1, mem2));

        when(storagePort.isManagedImageUrl(meId, "url")).thenReturn(true);
        Moment saved = Moment.create(UUID.randomUUID(), spaceId, meId, "url", "text", clock.instant());
        when(momentRepository.save(any(Moment.class))).thenReturn(saved);

        User author = new User(meId, "handle1", "nickname1", null, AuthProvider.APPLE, null,
                null, null, true, null, UserStatus.ACTIVE, Instant.now(), Instant.now());
        when(userRepository.findById(meId)).thenReturn(Optional.of(author));
        when(reactionRepository.findAllByMomentIdIn(any())).thenReturn(List.of());

        service.create(meId, new CreateMomentCommand(spaceId, "url", "text"));

        verify(pushPort, times(4)).send(any(PushMessage.class));
    }

    @Test
    @DisplayName("타인 모먼트 삭제는 FORBIDDEN")
    void 타인_모먼트_삭제_FORBIDDEN() {
        UUID authorId = UUID.randomUUID();
        Moment moment = Moment.create(UUID.randomUUID(), spaceId, authorId, "url", "text", clock.instant());
        when(momentRepository.findById(moment.id()))
                .thenReturn(Optional.of(moment));

        assertThatThrownBy(() -> service.delete(meId, moment.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("모먼트 생성: 알림 끈 멤버는 위젯 갱신만, 차단 관계 멤버는 아무것도 받지 않는다")
    void create_respectsMuteAndBlock() {
        UUID mutedId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        SpaceMember myMem = new SpaceMember(UUID.randomUUID(), spaceId, meId, MemberRole.OWNER, Instant.now(), null);
        SpaceMember muted = new SpaceMember(UUID.randomUUID(), spaceId, mutedId, MemberRole.MEMBER, Instant.now(), null)
                .withNotifications(false);
        SpaceMember blocked = new SpaceMember(UUID.randomUUID(), spaceId, blockedId, MemberRole.MEMBER, Instant.now(), null);
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(myMem));
        when(spaceRepository.findActiveMembersBySpaceId(spaceId)).thenReturn(List.of(myMem, muted, blocked));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(
                Space.group(spaceId, "주말 산책", 6, clock.instant())));
        when(blockPort.findBlockedUserIdsEitherDirection(meId)).thenReturn(Set.of(blockedId));
        Moment saved = Moment.create(UUID.randomUUID(), spaceId, meId, null, "text", clock.instant());
        when(momentRepository.save(any(Moment.class))).thenReturn(saved);
        User author = new User(meId, "handle1", "작성자", null, AuthProvider.APPLE, null,
                null, null, true, null, UserStatus.ACTIVE, Instant.now(), Instant.now());
        when(userRepository.findById(meId)).thenReturn(Optional.of(author));
        when(reactionRepository.findAllByMomentIdIn(any())).thenReturn(List.of());

        service.create(meId, new CreateMomentCommand(spaceId, null, "text"));

        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushPort, times(1)).send(push.capture());
        assertThat(push.getValue().targetUserId()).isEqualTo(mutedId);
        assertThat(push.getValue().type()).isEqualTo("WIDGET_REFRESH");
    }

    @Test
    @DisplayName("타임라인: 차단 관계 작성자를 저장소 조회에서 제외한다")
    void timeline_excludesBlockedAuthors() {
        UUID blockedId = UUID.randomUUID();
        SpaceMember myMem = new SpaceMember(UUID.randomUUID(), spaceId, meId, MemberRole.MEMBER, Instant.now(), null);
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(myMem));
        when(blockPort.findBlockedUserIdsEitherDirection(meId)).thenReturn(Set.of(blockedId));
        when(momentRepository.findTimeline(eq(spaceId), any(), any(), anyInt(), eq(Set.of(blockedId))))
                .thenReturn(new MomentRepositoryPort.TimelinePage(List.of(), null, false));

        service.timeline(meId, spaceId, null, 20);

        verify(momentRepository).findTimeline(eq(spaceId), any(), any(), anyInt(), eq(Set.of(blockedId)));
    }

    @Test
    @DisplayName("타임라인: 나와 차단 관계인 사용자의 리액션은 집계에서 뺀다")
    void timeline_hidesBlockedReactors() {
        UUID blockedId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        SpaceMember myMem = new SpaceMember(UUID.randomUUID(), spaceId, meId, MemberRole.MEMBER, Instant.now(), null);
        Moment moment = Moment.create(UUID.randomUUID(), spaceId, authorId, null, "글", Instant.now());
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(myMem));
        when(blockPort.findBlockedUserIdsEitherDirection(meId)).thenReturn(Set.of(blockedId));
        when(momentRepository.findTimeline(eq(spaceId), any(), any(), anyInt(), any()))
                .thenReturn(new MomentRepositoryPort.TimelinePage(List.of(moment), null, false));
        when(userRepository.findAllByIds(any())).thenReturn(List.of(new User(authorId, "author", "작성자", null,
                AuthProvider.APPLE, null, null, null, true, null, UserStatus.ACTIVE, Instant.now(), Instant.now())));
        when(reactionRepository.findAllByMomentIdIn(any())).thenReturn(List.of(
                com.moment.domain.moment.Reaction.create(UUID.randomUUID(), moment.id(), blockedId, "❤️", Instant.now()),
                com.moment.domain.moment.Reaction.create(UUID.randomUUID(), moment.id(), friendId, "❤️", Instant.now())));

        var page = service.timeline(meId, spaceId, null, 20);

        assertThat(page.items().get(0).reactions()).singleElement()
                .satisfies(r -> assertThat(r.count()).isEqualTo(1));
    }

    @Test
    @DisplayName("최신 1건(excludeMine): 나와 차단 관계 작성자를 함께 제외한다")
    void latest_excludesMeAndBlocked() {
        UUID blockedId = UUID.randomUUID();
        SpaceMember myMem = new SpaceMember(UUID.randomUUID(), spaceId, meId, MemberRole.MEMBER, Instant.now(), null);
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(myMem));
        when(blockPort.findBlockedUserIdsEitherDirection(meId)).thenReturn(Set.of(blockedId));
        when(momentRepository.findLatestBySpaceExcluding(spaceId, Set.of(meId, blockedId)))
                .thenReturn(Optional.empty());

        assertThat(service.latest(meId, spaceId, true)).isEmpty();
        verify(momentRepository).findLatestBySpaceExcluding(spaceId, Set.of(meId, blockedId));
    }

    @Test
    @DisplayName("멤버별 최신: 차단 관계 멤버는 조회 대상에서 빠진다")
    void latestPerMember_skipsBlocked() {
        UUID friendId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        SpaceMember myMem = new SpaceMember(UUID.randomUUID(), spaceId, meId, MemberRole.MEMBER, Instant.now(), null);
        SpaceMember friend = new SpaceMember(UUID.randomUUID(), spaceId, friendId, MemberRole.MEMBER, Instant.now(), null);
        SpaceMember blocked = new SpaceMember(UUID.randomUUID(), spaceId, blockedId, MemberRole.MEMBER, Instant.now(), null);
        when(spaceRepository.findActiveMembersBySpaceId(spaceId)).thenReturn(List.of(myMem, friend, blocked));
        when(blockPort.findBlockedUserIdsEitherDirection(meId)).thenReturn(Set.of(blockedId));
        when(momentRepository.findLatestPerAuthor(spaceId, List.of(meId, friendId))).thenReturn(List.of());

        assertThat(service.latestPerMember(meId, spaceId)).isEmpty();
        verify(momentRepository).findLatestPerAuthor(spaceId, List.of(meId, friendId));
    }
}
