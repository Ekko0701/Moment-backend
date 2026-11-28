package com.moment.domain.moment;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;

/**
 * 사진 위 글씨 색. 실제 색상 값은 클라이언트가 이름으로 정한다.
 */
public enum TextColor {
    WHITE,
    INK,
    PEACH,
    BUTTER,
    LILAC;

    /** 요청 값을 해석한다. 비어 있으면 null, 모르는 값이면 INVALID_REQUEST. */
    public static TextColor parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 글씨 색이에요.");
        }
    }
}
