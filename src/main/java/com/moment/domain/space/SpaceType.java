package com.moment.domain.space;

/**
 * 스페이스 유형.
 *
 * <p>v1.0은 {@link #ONE_TO_ONE}만 생성·동작한다. 1:1 제약은 스키마가 아니라
 * 서버 정책({@code moment.policy.single-space-enabled})과 사용자 행 잠금으로 강제된다.
 */
public enum SpaceType {
    /** 1:1 스페이스 — v1.0에서 유일하게 동작하는 유형 (멤버 정확히 2명). */
    ONE_TO_ONE,
    /**
     * 예약 값 — v2.0의 N인 스페이스용. v1.0에서는 생성 경로가 없으며 동작하지 않는다.
     * v2.0에서 별도의 그룹 생성·멤버십 서비스와 SpaceInvitation 모델로 구현 예정.
     */
    GROUP,
    /** 예약 값 — 전체 공개 스페이스용. v1.0에서는 생성 경로가 없으며 동작하지 않는다. */
    PUBLIC
}
