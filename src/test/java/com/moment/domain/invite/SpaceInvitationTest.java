package com.moment.domain.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.common.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpaceInvitationTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");
    private static final UUID FROM_USER = UUID.randomUUID();
    private static final UUID TO_USER = UUID.randomUUID();

    private SpaceInvitation createPending() {
        return new SpaceInvitation(
                UUID.randomUUID(),
                FROM_USER,
                TO_USER,
                InvitationVia.SEARCH,
                InvitationStatus.PENDING,
                NOW,
                null);
    }

    @Test
    @DisplayName("PENDING 상태에서만 accept 가능")
    void accept_onlyFromPending() {
        SpaceInvitation pending = createPending();

        SpaceInvitation accepted = pending.accept(NOW);

        assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(accepted.respondedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("ACCEPTED 상태에서는 accept 불가")
    void accept_fromAccepted_throws() {
        SpaceInvitation pending = createPending();
        SpaceInvitation accepted = pending.accept(NOW);

        assertThatThrownBy(() -> accepted.accept(NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("더 이상 변경할 수 없는");
    }

    @Test
    @DisplayName("PENDING 상태에서만 decline 가능")
    void decline_onlyFromPending() {
        SpaceInvitation pending = createPending();

        SpaceInvitation declined = pending.decline(NOW);

        assertThat(declined.status()).isEqualTo(InvitationStatus.DECLINED);
    }

    @Test
    @DisplayName("DECLINED에서는 다시 decline 불가")
    void decline_fromDeclined_throws() {
        SpaceInvitation pending = createPending();
        SpaceInvitation declined = pending.decline(NOW);

        assertThatThrownBy(() -> declined.decline(NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("TTL로 만료 여부 판정")
    void isExpiredBy_correctlyDetects() {
        SpaceInvitation pending = createPending();
        Duration ttl = Duration.ofHours(24);

        assertThat(pending.isExpiredBy(NOW.plus(Duration.ofHours(23)), ttl)).isFalse();
        assertThat(pending.isExpiredBy(NOW.plus(ttl).plus(Duration.ofSeconds(1)), ttl)).isTrue();
    }
}
