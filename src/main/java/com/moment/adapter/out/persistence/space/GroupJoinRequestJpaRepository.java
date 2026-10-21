package com.moment.adapter.out.persistence.space;

import com.moment.domain.space.JoinRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupJoinRequestJpaRepository extends JpaRepository<GroupJoinRequestEntity, UUID> {

    Optional<GroupJoinRequestEntity> findFirstBySpaceIdAndUserIdAndStatus(UUID spaceId, UUID userId,
            JoinRequestStatus status);

    List<GroupJoinRequestEntity> findBySpaceIdAndStatusOrderByCreatedAtAsc(UUID spaceId, JoinRequestStatus status);

    List<GroupJoinRequestEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, JoinRequestStatus status);

    /** [spaceId, count] 쌍 목록 */
    @Query("SELECT r.spaceId, COUNT(r) FROM GroupJoinRequestEntity r "
            + "WHERE r.spaceId IN :spaceIds AND r.status = :status GROUP BY r.spaceId")
    List<Object[]> countBySpaceIdsAndStatus(@Param("spaceIds") Collection<UUID> spaceIds,
            @Param("status") JoinRequestStatus status);
}
