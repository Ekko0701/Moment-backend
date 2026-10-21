package com.moment.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.application.port.out.BlockPort;
import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.ReportPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.safety.Block;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceType;
import com.moment.domain.space.SpaceStatus;
import com.moment.domain.space.MemberRole;
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

@DisplayName("SafetyService")
@ExtendWith(MockitoExtension.class)
class SafetyServiceTest {

    @Mock private ReportPort reportRepository;
    @Mock private BlockPort blockRepository;
    @Mock private MomentRepositoryPort momentRepository;
    @Mock private SpaceRepositoryPort spaceRepository;
    @Mock private com.moment.application.port.out.UserRepositoryPort userRepository;
    @Mock private PushPort pushPort;

    private SafetyService service;
    private Clock clock;
    private UUID meId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
        service = new SafetyService(reportRepository, blockRepository, momentRepository,
                spaceRepository, userRepository, pushPort, clock);
        meId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    @Test
    @DisplayName("자신을 차단하면 INVALID_REQUEST")
    void 자신_차단_INVALID_REQUEST() {
        assertThatThrownBy(() -> service.block(meId, meId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("차단: 함께 속한 그룹 스페이스는 종료하지 않는다(서로의 모먼트만 숨김)")
    void block_sharedGroup_keepsGroup() {
        Space group = Space.group(UUID.randomUUID(), "주말 산책", 6, clock.instant());
        SpaceMember mine = new SpaceMember(UUID.randomUUID(), group.id(), meId, MemberRole.MEMBER, clock.instant(), null);
        SpaceMember theirs = new SpaceMember(UUID.randomUUID(), group.id(), targetId, MemberRole.MEMBER, clock.instant(), null);
        when(blockRepository.existsBlockBetween(meId, targetId)).thenReturn(false);
        when(spaceRepository.findActiveMembershipsByUserId(meId)).thenReturn(List.of(mine));
        when(spaceRepository.findActiveMembershipsByUserId(targetId)).thenReturn(List.of(theirs));
        when(spaceRepository.findById(group.id())).thenReturn(Optional.of(group));

        service.block(meId, targetId);

        verify(blockRepository).save(any(Block.class));
        verify(spaceRepository, never()).save(any());
        verify(spaceRepository, never()).saveMember(any());
        verify(pushPort, never()).send(any());
    }
}
