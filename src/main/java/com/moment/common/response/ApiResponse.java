package com.moment.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API가 공유하는 응답 엔벨로프 (기능정의서 7장).
 * {@code { success, data, error, meta }}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error, Object meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message), null);
    }

    public record ErrorBody(String code, String message) {
    }
}
