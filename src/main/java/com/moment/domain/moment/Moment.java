package com.moment.domain.moment;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;

/**
 * 모먼트 도메인 모델 (기능정의서 F-03).
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record Moment(
        UUID id,
        UUID spaceId,
        UUID authorId,
        String imageUrl,
        String text,
        Instant createdAt,
        Instant deletedAt) {

    public static final int TEXT_MAX_LENGTH = 500;

    /**
     * 새 모먼트 생성.
     * imageUrl과 text 중 하나는 반드시 필요하며, text는 500자 이하여야 한다.
     */
    public static Moment create(UUID id, UUID spaceId, UUID authorId, String imageUrl,
            String text, Instant now) {
        if ((imageUrl == null || imageUrl.isBlank()) && (text == null || text.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "사진 또는 글 중 하나는 필요해요.");
        }
        if (text != null && text.length() > TEXT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "글은 " + TEXT_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        return new Moment(id, spaceId, authorId,
                imageUrl != null && !imageUrl.isBlank() ? imageUrl : null,
                text != null && !text.isBlank() ? text : null,
                now, null);
    }

    /**
     * 소프트 삭제 (deletedAt 설정).
     */
    public Moment delete(Instant now) {
        return new Moment(id, spaceId, authorId, imageUrl, text, createdAt, now);
    }

    /**
     * 삭제 여부 확인.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
