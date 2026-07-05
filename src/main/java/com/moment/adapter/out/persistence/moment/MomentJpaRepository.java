package com.moment.adapter.out.persistence.moment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MomentJpaRepository extends JpaRepository<MomentEntity, UUID> {

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND (:cursor IS NULL OR m.createdAt < :cursor) "
                    + "ORDER BY m.createdAt DESC "
                    + "LIMIT :limit")
    List<MomentEntity> findTimeline(@Param("spaceId") UUID spaceId, @Param("cursor") Instant cursor,
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
