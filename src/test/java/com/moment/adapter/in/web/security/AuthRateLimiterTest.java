package com.moment.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    @DisplayName("윈도우 내 한도(10)를 초과하면 TOO_MANY_REQUESTS를 던진다")
    void exceedsLimit_throws() {
        AuthRateLimiter limiter = new AuthRateLimiter(Clock.fixed(NOW, ZoneOffset.UTC), true);

        for (int i = 0; i < 10; i++) {
            int attempt = i;
            assertThatCode(() -> limiter.check("login-ip:1.2.3.4"))
                    .as("attempt %d", attempt)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.check("login-ip:1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("윈도우가 지나면 카운트가 초기화된다")
    void afterWindow_resets() {
        MutableClock clock = new MutableClock(NOW);
        AuthRateLimiter limiter = new AuthRateLimiter(clock, true);

        for (int i = 0; i < 10; i++) {
            limiter.check("k");
        }
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        assertThatCode(() -> limiter.check("k")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비활성(운영 외)에서는 한도를 강제하지 않는다")
    void disabled_neverThrows() {
        AuthRateLimiter limiter = new AuthRateLimiter(Clock.fixed(NOW, ZoneOffset.UTC), false);

        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                limiter.check("k");
            }
        }).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
