package com.moment.adapter.out.persistence.moment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MomentJpaRepository extends JpaRepository<MomentEntity, UUID> {

    // (createdAt, id) 복합 커서 — 같은 시각에 생성된 모먼트가 페이지 경계에서 누락되지 않도록
    // createdAt 동률일 때 id로 타이브레이크한다. 정렬도 (createdAt DESC, id DESC)로 일관.
    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND (:cursorCreatedAt IS NULL "
                    + "       OR m.createdAt < :cursorCreatedAt "
                    + "       OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) "
                    + "ORDER BY m.createdAt DESC, m.id DESC "
                    + "LIMIT :limit")
    List<MomentEntity> findTimeline(@Param("spaceId") UUID spaceId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt, @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND (:excludeAuthorId IS NULL OR m.authorId != :excludeAuthorId) "
                    + "ORDER BY m.createdAt DESC "
                    + "LIMIT 1")
    Optional<MomentEntity> findLatestBySpace(@Param("spaceId") UUID spaceId,
            @Param("excludeAuthorId") UUID excludeAuthorId);
}
