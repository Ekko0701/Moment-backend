package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private SpaceService service;

    @BeforeEach
    void setUp() {
        service = new SpaceService(spaceRepository, userRepository, pushPort,
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
}
