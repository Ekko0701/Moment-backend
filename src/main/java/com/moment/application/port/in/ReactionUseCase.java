package com.moment.application.port.in;

import java.util.UUID;

/**
 * 리액션 유스케이스 포트 (인바운드).
 */
public interface ReactionUseCase {

    /**
     * 모먼트에 리액션 추가 또는 변경.
     * - 기존 리액션 없음: 새 리액션 생성
     * - 기존 리액션 있고 emoji가 같음: 삭제 (토글 취소)
     * - 기존 리액션 있고 emoji가 다름: 교체
     *
     * @param meId 현재 사용자 ID
     * @param momentId 모먼트 ID
     * @param emoji 이모지
     */
    void react(UUID meId, UUID momentId, String emoji);

    /**
     * 리액션 삭제.
     * 리액션이 없으면 무시한다.
     *
     * @param meId 현재 사용자 ID
     * @param momentId 모먼트 ID
     */
    void remove(UUID meId, UUID momentId);
}
