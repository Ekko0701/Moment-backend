package com.moment.adapter.out.persistence.invite;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpaceInvitationJpaRepository extends JpaRepository<SpaceInvitationEntity, UUID> {

    @Query("SELECT e FROM SpaceInvitationEntity e WHERE (e.fromUserId = :userId OR e.toUserId = :userId) AND e.status = 'PENDING' ORDER BY e.createdAt DESC")
    List<SpaceInvitationEntity> findAllPendingByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT e FROM SpaceInvitationEntity e WHERE e.toUserId = :userId ORDER BY e.createdAt DESC")
    List<SpaceInvitationEntity> findByToUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT e FROM SpaceInvitationEntity e WHERE e.fromUserId = :userId ORDER BY e.createdAt DESC")
    List<SpaceInvitationEntity> findByFromUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN EXISTS (SELECT 1 FROM SpaceInvitationEntity e WHERE ((e.fromUserId = :a AND e.toUserId = :b) OR (e.fromUserId = :b AND e.toUserId = :a)) AND e.status = 'PENDING') THEN true ELSE false END")
    boolean existsPendingBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT CASE WHEN EXISTS (SELECT 1 FROM SpaceInvitationEntity e WHERE e.fromUserId = :from AND e.toUserId = :to AND e.status = 'DECLINED' AND e.createdAt > :since) THEN true ELSE false END")
    boolean existsDeclinedBetweenSince(@Param("from") UUID from, @Param("to") UUID to, @Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM SpaceInvitationEntity e WHERE e.fromUserId = :userId AND e.createdAt > :since")
    int countByFromUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
