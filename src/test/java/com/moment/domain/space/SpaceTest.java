package com.moment.domain.space;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpaceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

    @Test
    @DisplayName("1대1 스페이스 생성 시 maxMembers=2, status=ACTIVE")
    void oneToOne_creates() {
        UUID id = UUID.randomUUID();

        Space space = Space.oneToOne(id, NOW);

        assertThat(space.id()).isEqualTo(id);
        assertThat(space.type()).isEqualTo(SpaceType.ONE_TO_ONE);
        assertThat(space.maxMembers()).isEqualTo(2);
        assertThat(space.status()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(space.createdAt()).isEqualTo(NOW);
        assertThat(space.name()).isNull();
    }

    @Test
    @DisplayName("스페이스 비활성화")
    void deactivate_changesStatus() {
        Space space = Space.oneToOne(UUID.randomUUID(), NOW);

        Space deactivated = space.deactivate();

        assertThat(deactivated.status()).isEqualTo(SpaceStatus.INACTIVE);
        assertThat(deactivated.id()).isEqualTo(space.id());
        assertThat(deactivated.createdAt()).isEqualTo(space.createdAt());
    }

    @Test
    @DisplayName("비활성화된 스페이스는 불변 객체")
    void deactivate_returnsNewInstance() {
        Space space = Space.oneToOne(UUID.randomUUID(), NOW);

        Space deactivated = space.deactivate();

        assertThat(space.status()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(deactivated.status()).isEqualTo(SpaceStatus.INACTIVE);
    }

    @Test
    @DisplayName("그룹 스페이스 생성 시 이름과 정원을 기록하고 ACTIVE로 시작한다")
    void group_creates() {
        Space space = Space.group(UUID.randomUUID(), "주말 산책", 6, NOW);

        assertThat(space.type()).isEqualTo(SpaceType.GROUP);
        assertThat(space.isGroup()).isTrue();
        assertThat(space.name()).isEqualTo("주말 산책");
        assertThat(space.maxMembers()).isEqualTo(6);
        assertThat(space.isActive()).isTrue();
    }
}
