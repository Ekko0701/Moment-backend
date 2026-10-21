package com.moment.adapter.in.web.dto.space;

import com.moment.adapter.in.web.dto.moment.MomentResponse;
import java.util.UUID;

/**
 * @param latestMoment 이 멤버의 최신 모먼트. 없거나 나와 차단 관계면 null
 */
public record GroupMemberResponse(
        UUID userId,
        String handle,
        String nickname,
        String profileImageUrl,
        String role,
        long joinedAt,
        MomentResponse latestMoment) {
}
