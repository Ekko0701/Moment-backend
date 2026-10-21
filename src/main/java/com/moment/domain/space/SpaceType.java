package com.moment.domain.space;

/**
 * 스페이스 유형.
 *
 * <p>1:1 제약은 스키마가 아니라 서버 정책({@code moment.policy.single-space-enabled})과 사용자 행 잠금으로 강제된다.
 */
public enum SpaceType {
    /** 1:1 스페이스. 멤버는 정확히 2명이고, 한 명이 나가면 스페이스가 종료된다. */
    ONE_TO_ONE,
    /** 그룹 스페이스. 초대 코드와 방장 수락으로 정원({@code moment.policy.group-max-members})까지 참여한다. */
    GROUP,
    /** 예약 값. 생성 경로가 없다. */
    PUBLIC
}
