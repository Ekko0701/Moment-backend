package com.moment.domain.moment;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;

/**
 * 사진 위 글씨가 시작하는 모서리. 둘 다 왼쪽 정렬이다.
 */
public enum TextPosition {
    /** 왼쪽 위에서 시작해 아래로 이어진다. */
    TOP,
    /** 왼쪽 아래에서 시작해 위로 쌓인다. */
    BOTTOM;

    /** 요청 값을 해석한다. 비어 있으면 null, 모르는 값이면 INVALID_REQUEST. */
    public static TextPosition parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "글씨 위치는 TOP 또는 BOTTOM이어야 해요.");
        }
    }
}
