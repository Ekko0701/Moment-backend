package com.moment.domain.space;

/** 그룹 참여 요청 상태. PENDING에서만 다른 상태로 바뀐다. */
public enum JoinRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED
}
