package com.moment.adapter.out.persistence.block;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BlockJpaRepository extends JpaRepository<BlockEntity, UUID> {

    Optional<BlockEntity> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query(
            "SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END "
                    + "FROM BlockEntity e "
                    + "WHERE (e.blockerId = :a AND e.blockedId = :b) "
                    + "   OR (e.blockerId = :b AND e.blockedId = :a)")
    boolean existsBlockBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT e.blockedId FROM BlockEntity e WHERE e.blockerId = :userId")
    List<UUID> findBlockedIdsByBlocker(@Param("userId") UUID userId);

    @Query("SELECT e.blockerId FROM BlockEntity e WHERE e.blockedId = :userId")
    List<UUID> findBlockerIdsByBlocked(@Param("userId") UUID userId);
}
