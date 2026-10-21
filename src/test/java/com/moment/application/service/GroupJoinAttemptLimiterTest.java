package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GroupJoinAttemptLimiter")
class GroupJoinAttemptLimiterTest {

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final GroupJoinAttemptLimiter limiter = new GroupJoinAttemptLimiter(clock);
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("10분 안에 5번 실패하면 잠기고, 10분이 지나면 풀린다")
    void locksAfterFiveFailuresAndUnlocksAfterTenMinutes() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(userId);
        }
        assertThatCode(() -> limiter.ensureAllowed(userId)).doesNotThrowAnyException();

        limiter.recordFailure(userId);
        assertThatThrownBy(() -> limiter.ensureAllowed(userId))
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_REQUESTS);

        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> limiter.ensureAllowed(userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("10분보다 오래된 실패는 세지 않는다")
    void oldFailuresExpire() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(userId);
        }
        clock.advance(Duration.ofMinutes(11));
        limiter.recordFailure(userId);

        assertThatCode(() -> limiter.ensureAllowed(userId)).doesNotThrowAnyException();
    }
}
