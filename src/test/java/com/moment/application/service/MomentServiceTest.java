package com.moment.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.application.port.in.MomentUseCase.CreateMomentCommand;
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

    private MomentService service;
    private Clock clock;
    private UUID meId;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
        service = new MomentService(momentRepository, reactionRepository, storagePort,
                spaceRepository, userRepository, pushPort, clock);
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

        Moment saved = Moment.create(UUID.randomUUID(), spaceId, meId, "url", "text", clock.instant());
        when(momentRepository.save(any(Moment.class))).thenReturn(saved);

        User author = new User(meId, "handle1", "nickname1", null, AuthProvider.APPLE, null,
                true, null, UserStatus.ACTIVE, Instant.now(), Instant.now());
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
}
