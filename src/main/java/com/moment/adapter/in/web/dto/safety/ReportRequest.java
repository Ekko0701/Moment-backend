package com.moment.adapter.in.web.dto.safety;

import com.moment.domain.safety.ReportReason;
import java.util.UUID;

public record ReportRequest(UUID momentId, ReportReason reason, String detail) {
}
