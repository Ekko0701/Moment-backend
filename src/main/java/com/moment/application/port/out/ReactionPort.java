package com.moment.application.port.out;

import com.moment.domain.moment.Reaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 리액션 저장소 포트 (아웃바운드).
 */
public interface ReactionPort {

    Reaction save(Reaction reaction);

    Optional<Reaction> findByMomentIdAndUserId(UUID momentId, UUID userId);

    void deleteById(UUID id);

    /**
     * 여러 모먼트의 모든 리액션 조회 (타임라인 집계용).
     */
    List<Reaction> findAllByMomentIdIn(List<UUID> momentIds);
}
