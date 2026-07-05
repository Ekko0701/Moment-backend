package com.moment.application.port.out;

import com.moment.domain.moment.Moment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 모먼트 저장소 포트 (아웃바운드).
 */
public interface MomentRepositoryPort {

    Moment save(Moment moment);

    Optional<Moment> findById(UUID id);

    /**
     * 스페이스 타임라인 조회.
     * - deletedAt이 null인 모먼트만 반환
     * - createdAt이 cursor보다 이전인 모먼트만 반환 (cursor가 null이면 제약 없음)
     * - createdAt 기준 최신순 정렬
     * - limit건까지만 반환
     */
    TimelinePage findTimeline(UUID spaceId, Instant cursor, int limit);

    /**
     * 스페이스의 최신 모먼트 조회.
     * - deletedAt이 null인 모먼트만 반환
     * - excludeAuthorId가 null이 아니면 해당 사용자의 모먼트는 제외
     * - createdAt이 가장 최신인 1건 반환
     */
    Optional<Moment> findLatestBySpace(UUID spaceId, UUID excludeAuthorId);

    record TimelinePage(List<Moment> items, Instant nextCursor, boolean hasNext) {
    }
}
