package com.moment.adapter.in.web.dto.invite;

import java.time.Instant;

public record IssueCodeResponse(String code, Instant expiresAt) {
}
