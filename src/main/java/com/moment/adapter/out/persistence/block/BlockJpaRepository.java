package com.moment.adapter.out.persistence.block;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BlockJpaRepository extends JpaRepository<BlockEntity, UUID> {

    @Query(
            "SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END "
                    + "FROM BlockEntity e "
                    + "WHERE (e.blockerId = :a AND e.blockedId = :b) "
                    + "   OR (e.blockerId = :b AND e.blockedId = :a)")
    boolean existsBlockBetween(@Param("a") UUID a, @Param("b") UUID b);
}
