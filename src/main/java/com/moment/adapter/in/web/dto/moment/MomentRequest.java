package com.moment.adapter.in.web.dto.moment;

import java.util.UUID;

public record MomentRequest(UUID spaceId, String imageUrl, String text) {
}
