package com.moment.adapter.in.web.dto.safety;

import com.moment.domain.safety.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReportRequest(
        @NotNull(message = "신고 대상 모먼트는 필수입니다.")
        UUID momentId,
        @NotNull(message = "신고 사유는 필수입니다.")
        ReportReason reason,
        // DB 컬럼(reports.detail VARCHAR(500))과 일치시켜 무결성 오류 대신 400을 낸다
        @Size(max = 500, message = "상세 사유는 500자 이하로 입력해 주세요.")
        String detail) {
}
