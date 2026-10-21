package com.moment.application.port.out;

import com.moment.domain.moment.Moment;
import java.time.Instant;
import java.util.Collection;
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
     * - (createdAt, id) 복합 커서보다 이전인 모먼트만 반환 (cursorCreatedAt이 null이면 제약 없음)
     * - (createdAt DESC, id DESC) 정렬 — 동시각 타이 시 id로 안정 정렬
     * - limit건까지만 반환
     */
    TimelinePage findTimeline(UUID spaceId, Instant cursorCreatedAt, UUID cursorId, int limit);

    /**
     * 스페이스의 최신 모먼트 조회.
     * - deletedAt이 null인 모먼트만 반환
     * - excludeAuthorId가 null이 아니면 해당 사용자의 모먼트는 제외
     * - createdAt이 가장 최신인 1건 반환
     */
    Optional<Moment> findLatestBySpace(UUID spaceId, UUID excludeAuthorId);

    /**
     * findTimeline과 같은 규칙에 excludedAuthorIds 작성자를 제외한다. 빈 컬렉션이면 제외 없음.
     */
    TimelinePage findTimeline(UUID spaceId, Instant cursorCreatedAt, UUID cursorId, int limit,
            Collection<UUID> excludedAuthorIds);

    /** 스페이스의 최신 모먼트 1건. excludedAuthorIds 작성자는 제외하며 빈 컬렉션이면 제외 없음. */
    Optional<Moment> findLatestBySpaceExcluding(UUID spaceId, Collection<UUID> excludedAuthorIds);

    /** 스페이스 안에서 authorIds 각자의 최신 모먼트 1건씩. 모먼트가 없는 작성자는 결과에 없다. */
    List<Moment> findLatestPerAuthor(UUID spaceId, Collection<UUID> authorIds);

    /**
     * spaceIds 각각의 최신 모먼트 1건씩. excludedAuthorIds는 비어 있으면 안 된다(호출자는 최소한 본인을 넣는다).
     * 모먼트가 없는 스페이스는 결과에 없다.
     */
    List<Moment> findLatestPerSpace(Collection<UUID> spaceIds, Collection<UUID> excludedAuthorIds);

    record TimelinePage(List<Moment> items, Instant nextCursor, boolean hasNext) {
    }
}
