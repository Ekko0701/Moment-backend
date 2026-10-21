package com.moment.application.service;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 그룹 코드 참여 실패 제한. 사용자별로 10분 안에 틀린 코드를 5번 넣으면 10분 동안 참여 요청을 막는다.
 *
 * <p>코드를 무작위로 대입해 남의 그룹을 찾는 시도를 늦춘다. 서버 메모리에만 기록하므로
 * 인스턴스를 여러 대로 늘리면 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
public class GroupJoinAttemptLimiter {

    static final Duration WINDOW = Duration.ofMinutes(10);
    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(10);
    // 기록하는 사용자 수가 이 값을 넘으면 만료된 기록을 정리해 메모리 사용을 묶어 둔다
    private static final int MAX_TRACKED_USERS = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<UUID, Attempts> attempts = new ConcurrentHashMap<>();

    public GroupJoinAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 잠긴 사용자면 TOO_MANY_REQUESTS를 던진다. */
    public void ensureAllowed(UUID userId) {
        Attempts current = attempts.get(userId);
        if (current != null && current.isLocked(clock.instant())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "코드를 여러 번 잘못 입력했어요. 10분 뒤에 다시 시도해 주세요.");
        }
    }

    /** 틀린 코드 입력을 기록한다. 창 안의 실패가 한도에 닿으면 잠근다. */
    public void recordFailure(UUID userId) {
        Instant now = clock.instant();
        if (attempts.size() > MAX_TRACKED_USERS) {
            attempts.entrySet().removeIf(e -> e.getValue().isStale(now));
        }
        attempts.compute(userId, (id, existing) -> {
            Attempts next = existing == null ? new Attempts() : existing;
            next.recordFailure(now);
            return next;
        });
    }

    private static final class Attempts {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;

        synchronized void recordFailure(Instant now) {
            failures.removeIf(at -> !at.isAfter(now.minus(WINDOW)));
            failures.addLast(now);
            if (failures.size() >= MAX_FAILURES) {
                lockedUntil = now.plus(LOCK);
                failures.clear();
            }
        }

        synchronized boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        synchronized boolean isStale(Instant now) {
            boolean noRecentFailure = failures.isEmpty() || !failures.peekLast().isAfter(now.minus(WINDOW));
            return noRecentFailure && !isLocked(now);
        }
    }
}
