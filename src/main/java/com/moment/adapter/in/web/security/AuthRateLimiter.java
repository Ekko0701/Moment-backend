package com.moment.adapter.in.web.security;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 인증 엔드포인트용 경량 고정 윈도우 rate limiter.
 *
 * <p>운영(prod)에서만 강제한다. 단일 인스턴스 메모리 기반이므로 다중 인스턴스로 확장하면
 * Redis 등 공유 저장소 기반 리미터로 교체해야 한다(현재는 무차별 로그인·BCrypt CPU 소진에
 * 대한 1차 방어).
 */
@Component
public class AuthRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_REQUESTS_PER_WINDOW = 10;

    private final Clock clock;
    private final boolean enabled;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AuthRateLimiter(Clock clock, org.springframework.core.env.Environment environment) {
        this(clock, java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod"));
    }

    // 테스트에서 enabled를 직접 지정할 수 있도록 분리한 생성자.
    AuthRateLimiter(Clock clock, boolean enabled) {
        this.clock = clock;
        this.enabled = enabled;
    }

    /** key에 대해 윈도우 내 요청 수를 증가시키고, 한도를 넘으면 TOO_MANY_REQUESTS를 던진다. */
    public void check(String key) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.start().plus(WINDOW))) {
                return new Window(now, 1);
            }
            return new Window(existing.start(), existing.count() + 1);
        });
        if (window.count() > MAX_REQUESTS_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.");
        }
    }

    private record Window(Instant start, int count) {
    }
}
