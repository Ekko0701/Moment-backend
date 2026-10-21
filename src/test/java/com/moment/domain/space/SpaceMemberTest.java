package com.moment.domain.space;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpaceMemberTest {

    private static final Instant JOINED = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    private SpaceMember member(MemberRole role) {
        return new SpaceMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), role, JOINED, null);
    }

    @Test
    @DisplayName("6인자 생성자는 알림을 켠 상태로 만든다")
    void defaultConstructor_enablesNotifications() {
        assertThat(member(MemberRole.MEMBER).notificationsEnabled()).isTrue();
    }

    @Test
    @DisplayName("나가기는 알림 설정과 역할을 유지한 채 leftAt만 기록한다")
    void leave_keepsRoleAndNotifications() {
        SpaceMember muted = member(MemberRole.OWNER).withNotifications(false);

        SpaceMember left = muted.leave(NOW);

        assertThat(left.isActive()).isFalse();
        assertThat(left.leftAt()).isEqualTo(NOW);
        assertThat(left.role()).isEqualTo(MemberRole.OWNER);
        assertThat(left.notificationsEnabled()).isFalse();
    }

    @Test
    @DisplayName("재입장은 같은 행을 일반 멤버로 되살리고 가입 시각과 알림을 초기화한다")
    void rejoin_reactivatesAsMember() {
        SpaceMember left = member(MemberRole.OWNER).withNotifications(false).leave(NOW.minusSeconds(60));

        SpaceMember rejoined = left.rejoin(NOW);

        assertThat(rejoined.id()).isEqualTo(left.id());
        assertThat(rejoined.isActive()).isTrue();
        assertThat(rejoined.role()).isEqualTo(MemberRole.MEMBER);
        assertThat(rejoined.joinedAt()).isEqualTo(NOW);
        assertThat(rejoined.notificationsEnabled()).isTrue();
    }

    @Test
    @DisplayName("방장 승격은 역할만 OWNER로 바꾼다")
    void promoteToOwner_changesRoleOnly() {
        SpaceMember m = member(MemberRole.MEMBER);

        SpaceMember owner = m.promoteToOwner();

        assertThat(owner.isOwner()).isTrue();
        assertThat(owner.joinedAt()).isEqualTo(m.joinedAt());
        assertThat(owner.isActive()).isTrue();
    }
}
