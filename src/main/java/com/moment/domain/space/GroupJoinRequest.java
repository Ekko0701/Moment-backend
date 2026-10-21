package com.moment.domain.space;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;

/**
 * 그룹 참여 요청. 초대 코드를 입력하면 PENDING으로 생기고, 방장이 수락하거나 거절한다. 요청자는 취소할 수 있다.
 * 불변 객체이며 상태 전이는 PENDING에서만 가능하다.
 */
public record GroupJoinRequest(
        UUID id,
        UUID spaceId,
        UUID userId,
        JoinRequestStatus status,
        Instant createdAt,
        Instant decidedAt) {

    public static GroupJoinRequest request(UUID id, UUID spaceId, UUID userId, Instant now) {
        return new GroupJoinRequest(id, spaceId, userId, JoinRequestStatus.PENDING, now, null);
    }

    public GroupJoinRequest approve(Instant now) {
        return decide(JoinRequestStatus.APPROVED, now);
    }

    public GroupJoinRequest reject(Instant now) {
        return decide(JoinRequestStatus.REJECTED, now);
    }

    public GroupJoinRequest cancel(Instant now) {
        return decide(JoinRequestStatus.CANCELED, now);
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    private GroupJoinRequest decide(JoinRequestStatus next, Instant now) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 요청이에요.");
        }
        return new GroupJoinRequest(id, spaceId, userId, next, createdAt, now);
    }
}
