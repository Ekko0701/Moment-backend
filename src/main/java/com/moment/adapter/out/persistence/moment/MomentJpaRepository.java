package com.moment.adapter.out.persistence.moment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MomentJpaRepository extends JpaRepository<MomentEntity, UUID> {

    // 커서 유무는 어댑터가 자바에서 분기한다 — "(:param IS NULL OR ...)" 패턴은
    // PostgreSQL이 파라미터 타입을 추론하지 못해 준비 단계에서 거부한다
    // (could not determine data type of parameter). H2에서만 통과하는 함정이므로 금지.

    // (createdAt, id) 복합 커서 — 같은 시각에 생성된 모먼트가 페이지 경계에서 누락되지 않도록
    // createdAt 동률일 때 id로 타이브레이크한다. 정렬도 (createdAt DESC, id DESC)로 일관.
    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "ORDER BY m.createdAt DESC, m.id DESC "
                    + "LIMIT :limit")
    List<MomentEntity> findTimelineFirstPage(@Param("spaceId") UUID spaceId,
            @Param("limit") int limit);

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND (m.createdAt < :cursorCreatedAt "
                    + "       OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) "
                    + "ORDER BY m.createdAt DESC, m.id DESC "
                    + "LIMIT :limit")
    List<MomentEntity> findTimelineAfterCursor(@Param("spaceId") UUID spaceId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt, @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "ORDER BY m.createdAt DESC "
                    + "LIMIT 1")
    Optional<MomentEntity> findLatestBySpace(@Param("spaceId") UUID spaceId);

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND m.authorId != :excludeAuthorId "
                    + "ORDER BY m.createdAt DESC "
                    + "LIMIT 1")
    Optional<MomentEntity> findLatestBySpaceExcludingAuthor(@Param("spaceId") UUID spaceId,
            @Param("excludeAuthorId") UUID excludeAuthorId);

    // 아래 *Excluding 쿼리의 :excluded는 비어 있으면 안 된다(빈 IN 목록은 DB마다 동작이 다르다).
    // 비어 있으면 어댑터가 제외 조건 없는 쿼리를 쓴다.

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND m.authorId NOT IN :excluded "
                    + "ORDER BY m.createdAt DESC, m.id DESC "
                    + "LIMIT :limit")
    List<MomentEntity> findTimelineFirstPageExcluding(@Param("spaceId") UUID spaceId,
            @Param("excluded") Collection<UUID> excluded, @Param("limit") int limit);

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND m.authorId NOT IN :excluded "
                    + "  AND (m.createdAt < :cursorCreatedAt "
                    + "       OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) "
                    + "ORDER BY m.createdAt DESC, m.id DESC "
                    + "LIMIT :limit")
    List<MomentEntity> findTimelineAfterCursorExcluding(@Param("spaceId") UUID spaceId,
            @Param("excluded") Collection<UUID> excluded,
            @Param("cursorCreatedAt") Instant cursorCreatedAt, @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.deletedAt IS NULL "
                    + "  AND m.authorId NOT IN :excluded "
                    + "ORDER BY m.createdAt DESC, m.id DESC "
                    + "LIMIT 1")
    Optional<MomentEntity> findLatestBySpaceExcludingAuthors(@Param("spaceId") UUID spaceId,
            @Param("excluded") Collection<UUID> excluded);

    @Query(
            "SELECT m FROM MomentEntity m "
                    + "WHERE m.spaceId = :spaceId "
                    + "  AND m.authorId = :authorId "
                    + "  AND m.deletedAt IS NULL "
                    + "ORDER BY m.createdAt DESC, m.id DESC "
                    + "LIMIT 1")
    Optional<MomentEntity> findLatestBySpaceAndAuthor(@Param("spaceId") UUID spaceId,
            @Param("authorId") UUID authorId);
}
