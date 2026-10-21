package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.out.BlockPort;
import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.ReactionPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.moment.Moment;
import com.moment.domain.moment.Reaction;
import com.moment.domain.user.User;
import com.moment.domain.user.AuthProvider;
import com.moment.domain.user.UserStatus;
import com.moment.domain.space.SpaceMember;
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

@DisplayName("ReactionService")
@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock private ReactionPort reactionRepository;
    @Mock private MomentRepositoryPort momentRepository;
    @Mock private SpaceRepositoryPort spaceRepository;
    @Mock private UserRepositoryPort userRepository;
    @Mock private PushPort pushPort;
    @Mock private BlockPort blockPort;

    private ReactionService service;
    private Clock clock;
    private UUID meId;
    private UUID momentId;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
        service = new ReactionService(reactionRepository, momentRepository, spaceRepository, userRepository, pushPort, blockPort, clock);
        meId = UUID.randomUUID();
        momentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("작성자와 차단 관계면 반응할 수 없고 저장도 알림도 하지 않는다")
    void blockedAuthor_forbidden() {
        UUID spaceId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Moment moment = Moment.create(momentId, spaceId, authorId, "url", "text", clock.instant());
        when(momentRepository.findById(momentId)).thenReturn(Optional.of(moment));
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(
                new SpaceMember(UUID.randomUUID(), spaceId, meId, com.moment.domain.space.MemberRole.MEMBER,
                        clock.instant(), null)));
        when(blockPort.existsBlockBetween(meId, authorId)).thenReturn(true);

        assertThatThrownBy(() -> service.react(meId, momentId, "❤️"))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(reactionRepository, never()).save(any());
        verify(pushPort, never()).send(any());
    }

    @Test
    @DisplayName("같은 이모지: 토글 취소")
    void 같은_이모지_토글_취소() {
        UUID spaceId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Moment moment = Moment.create(momentId, spaceId, authorId, "url", "text", clock.instant());
        when(momentRepository.findById(momentId)).thenReturn(Optional.of(moment));

        // Mock space membership
        SpaceMember member = new SpaceMember(UUID.randomUUID(), spaceId, meId, com.moment.domain.space.MemberRole.MEMBER, clock.instant(), null);
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(member));

        Reaction existing = Reaction.create(UUID.randomUUID(), momentId, meId, "❤️", clock.instant());
        when(reactionRepository.findByMomentIdAndUserId(momentId, meId)).thenReturn(Optional.of(existing));

        service.react(meId, momentId, "❤️");

        verify(reactionRepository).deleteById(existing.id());
        verify(reactionRepository, never()).save(any(Reaction.class));
        verify(pushPort, never()).send(any());
    }

    @Test
    @DisplayName("본인 모먼트 리액션: 푸시 미발송")
    void 본인_모먼트_리액션_푸시_미발송() {
        UUID spaceId = UUID.randomUUID();
        Moment moment = Moment.create(momentId, spaceId, meId, "url", "text", clock.instant());
        when(momentRepository.findById(momentId)).thenReturn(Optional.of(moment));

        // Mock space membership
        SpaceMember member = new SpaceMember(UUID.randomUUID(), spaceId, meId, com.moment.domain.space.MemberRole.MEMBER, clock.instant(), null);
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(member));

        when(reactionRepository.findByMomentIdAndUserId(momentId, meId)).thenReturn(Optional.empty());

        service.react(meId, momentId, "❤️");

        verify(pushPort, never()).send(any());
    }
}
