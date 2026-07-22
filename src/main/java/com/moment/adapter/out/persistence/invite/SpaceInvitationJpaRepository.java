package com.moment.adapter.out.persistence.invite;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpaceInvitationJpaRepository extends JpaRepository<SpaceInvitationEntity, UUID> {

    // 수락/거절/취소 응답 시 초대 행을 잠가 동시 수락으로 인한 이중 처리·이중 스페이스 생성을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM SpaceInvitationEntity e WHERE e.id = :id")
    Optional<SpaceInvitationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT e FROM SpaceInvitationEntity e WHERE (e.fromUserId = :userId OR e.toUserId = :userId) AND e.status = 'PENDING' ORDER BY e.createdAt DESC")
    List<SpaceInvitationEntity> findAllPendingByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT e FROM SpaceInvitationEntity e WHERE e.toUserId = :userId ORDER BY e.createdAt DESC")
    List<SpaceInvitationEntity> findByToUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT e FROM SpaceInvitationEntity e WHERE e.fromUserId = :userId ORDER BY e.createdAt DESC")
    List<SpaceInvitationEntity> findByFromUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    // TTL 조건을 조회에 포함한다 — 만료 상태 저장(EXPIRED 마킹)에 의존하면 예외 rollback으로
    // PENDING이 남아 재초대를 영구히 막을 수 있으므로, "유효한 PENDING"만 차단 근거로 삼는다.
    @Query("SELECT CASE WHEN EXISTS (SELECT 1 FROM SpaceInvitationEntity e WHERE ((e.fromUserId = :a AND e.toUserId = :b) OR (e.fromUserId = :b AND e.toUserId = :a)) AND e.status = 'PENDING' AND e.createdAt > :since) THEN true ELSE false END")
    boolean existsPendingBetween(@Param("a") UUID a, @Param("b") UUID b, @Param("since") Instant since);

    @Query("SELECT CASE WHEN EXISTS (SELECT 1 FROM SpaceInvitationEntity e WHERE e.fromUserId = :from AND e.toUserId = :to AND e.status = 'DECLINED' AND e.createdAt > :since) THEN true ELSE false END")
    boolean existsDeclinedBetweenSince(@Param("from") UUID from, @Param("to") UUID to, @Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM SpaceInvitationEntity e WHERE e.fromUserId = :userId AND e.createdAt > :since")
    int countByFromUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
