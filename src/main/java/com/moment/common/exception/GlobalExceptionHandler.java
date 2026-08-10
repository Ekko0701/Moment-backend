package com.moment.common.exception;

import com.moment.common.response.ApiResponse;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("business_error code={} message={}", code.name(), ex.getMessage(), ex);
        } else {
            log.warn("business_error code={} message={}", code.name(), ex.getMessage());
        }
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.name(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("unreadable_request message={}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.name(),
                        ErrorCode.INVALID_REQUEST.getMessage()));
    }

    // 잘못된 UUID 경로/쿼리, 파싱 불가한 날짜 등 타입 변환 실패 → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("type_mismatch param={} value={}", ex.getName(), ex.getValue());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.name(),
                        "요청 값의 형식이 올바르지 않아요."));
    }

    // 필수 쿼리 파라미터 누락 → 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("missing_param name={}", ex.getParameterName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.name(),
                        ex.getParameterName() + " 파라미터가 필요해요."));
    }

    // 동시성 충돌(데드락 패자·잠금 실패 등) → 500 대신 409로 매핑.
    // 같은 사용자의 요청 둘이 서로의 잠금을 기다리면 PostgreSQL이 한쪽을 강제 중단시키는데
    // (예: 서로 다른 초대 동시 수락), 패자 트랜잭션은 전부 롤백되어 데이터는 안전하므로
    // "동시 요청 충돌"이라는 의미에 맞게 CONFLICT로 응답한다. 재시도하면 정상 결과를 받는다.
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleConcurrencyFailure(ConcurrencyFailureException ex) {
        log.warn("concurrency_conflict message={}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.getStatus())
                .body(ApiResponse.error(ErrorCode.CONFLICT.name(),
                        "동시에 처리 중인 요청이 있어요. 잠시 후 다시 시도해 주세요."));
    }

    // DB 제약 위반(유니크·NOT NULL 등) → 500 대신 409로 매핑
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("data_integrity_violation message={}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.getStatus())
                .body(ApiResponse.error(ErrorCode.CONFLICT.name(),
                        "요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected_error", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(),
                        ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
