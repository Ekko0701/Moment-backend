package com.moment.adapter.out.persistence.space;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupInviteCodeJpaRepository extends JpaRepository<GroupInviteCodeEntity, String> {

    Optional<GroupInviteCodeEntity> findBySpaceId(UUID spaceId);

    // 즉시 DELETE를 실행해, 같은 트랜잭션에서 뒤따르는 새 코드 INSERT가 UNIQUE(space_id)에 걸리지 않게 한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupInviteCodeEntity e WHERE e.spaceId = :spaceId")
    void deleteBySpaceId(@Param("spaceId") UUID spaceId);
}
