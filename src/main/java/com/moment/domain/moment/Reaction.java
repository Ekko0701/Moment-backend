package com.moment.domain.moment;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 감정표현(리액션) 도메인 모델 (기능정의서 F-04).
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record Reaction(
        UUID id,
        UUID momentId,
        UUID userId,
        String emoji,
        Instant createdAt) {

    public static final Set<String> ALLOWED_EMOJIS = Set.of("❤️", "😊", "😂", "😢", "😮", "👍");

    /**
     * 새 리액션 생성.
     * emoji는 미리 정의된 6가지 이모지만 허용된다.
     */
    public static Reaction create(UUID id, UUID momentId, UUID userId, String emoji, Instant now) {
        if (!ALLOWED_EMOJIS.contains(emoji)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 감정표현이에요.");
        }
        return new Reaction(id, momentId, userId, emoji, now);
    }
}
