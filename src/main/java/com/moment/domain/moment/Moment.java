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
        TextPosition textPosition,
        TextColor textColor,
        Instant createdAt,
        Instant deletedAt) {

    public static final int TEXT_MAX_LENGTH = 500;

    /**
     * 글씨 위치·색을 지정하지 않은 새 모먼트 생성. 사진과 글이 함께 있으면 기본값(TOP, WHITE)이 들어간다.
     */
    public static Moment create(UUID id, UUID spaceId, UUID authorId, String imageUrl,
            String text, Instant now) {
        return create(id, spaceId, authorId, imageUrl, text, null, null, now);
    }

    /**
     * 새 모먼트 생성.
     * imageUrl과 text 중 하나는 반드시 필요하며, text는 500자 이하여야 한다.
     * 글씨 위치·색은 사진 위에 글씨를 올릴 때만 의미가 있어, 사진과 글이 함께 있을 때만 저장한다.
     * 그때 값이 비어 있으면 TOP, WHITE로 채운다.
     */
    public static Moment create(UUID id, UUID spaceId, UUID authorId, String imageUrl,
            String text, TextPosition textPosition, TextColor textColor, Instant now) {
        if ((imageUrl == null || imageUrl.isBlank()) && (text == null || text.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "사진 또는 글 중 하나는 필요해요.");
        }
        if (text != null && text.length() > TEXT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "글은 " + TEXT_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        String normalizedImageUrl = imageUrl != null && !imageUrl.isBlank() ? imageUrl : null;
        String normalizedText = text != null && !text.isBlank() ? text : null;
        boolean hasOverlay = normalizedImageUrl != null && normalizedText != null;
        return new Moment(id, spaceId, authorId, normalizedImageUrl, normalizedText,
                hasOverlay ? (textPosition != null ? textPosition : TextPosition.TOP) : null,
                hasOverlay ? (textColor != null ? textColor : TextColor.WHITE) : null,
                now, null);
    }

    /**
     * 소프트 삭제 (deletedAt 설정).
     */
    public Moment delete(Instant now) {
        return new Moment(id, spaceId, authorId, imageUrl, text, textPosition, textColor, createdAt, now);
    }

    /**
     * 삭제 여부 확인.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
