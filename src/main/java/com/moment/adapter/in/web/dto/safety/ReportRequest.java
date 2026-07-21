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
        @Size(max = 1000, message = "상세 사유가 너무 깁니다.")
        String detail) {
}
