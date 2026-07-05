package com.moment.domain.invite;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 스페이스 초대 도메인 모델.
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 * 상태 전이는 PENDING 상태에서만 가능하다.
 */
public record SpaceInvitation(
        UUID id,
        UUID fromUserId,
        UUID toUserId,
        InvitationVia via,
        InvitationStatus status,
        Instant createdAt,
        Instant respondedAt) {

    /**
     * 초대 수락 — PENDING이 아니면 예외.
     */
    public SpaceInvitation accept(Instant now) {
        if (status != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "더 이상 변경할 수 없는 상태예요.");
        }
        return new SpaceInvitation(id, fromUserId, toUserId, via, InvitationStatus.ACCEPTED, createdAt, now);
    }

    /**
     * 초대 거절 — PENDING이 아니면 예외.
     */
    public SpaceInvitation decline(Instant now) {
        if (status != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "더 이상 변경할 수 없는 상태예요.");
        }
        return new SpaceInvitation(id, fromUserId, toUserId, via, InvitationStatus.DECLINED, createdAt, now);
    }

    /**
     * 초대 취소 — PENDING이 아니면 예외.
     */
    public SpaceInvitation cancel(Instant now) {
        if (status != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "더 이상 변경할 수 없는 상태예요.");
        }
        return new SpaceInvitation(id, fromUserId, toUserId, via, InvitationStatus.CANCELED, createdAt, now);
    }

    /**
     * 초대 만료 — PENDING이 아니면 예외.
     */
    public SpaceInvitation expire(Instant now) {
        if (status != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "더 이상 변경할 수 없는 상태예요.");
        }
        return new SpaceInvitation(id, fromUserId, toUserId, via, InvitationStatus.EXPIRED, createdAt, now);
    }

    /**
     * 주어진 TTL로 만료 여부 확인.
     */
    public boolean isExpiredBy(Instant now, Duration ttl) {
        return createdAt.plus(ttl).isBefore(now);
    }
}
