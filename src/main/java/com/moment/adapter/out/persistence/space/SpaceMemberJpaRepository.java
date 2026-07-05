package com.moment.adapter.out.persistence.space;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpaceMemberJpaRepository extends JpaRepository<SpaceMemberEntity, UUID> {

    Optional<SpaceMemberEntity> findBySpaceIdAndUserId(UUID spaceId, UUID userId);

    List<SpaceMemberEntity> findBySpaceId(UUID spaceId);

    List<SpaceMemberEntity> findByUserIdAndLeftAtIsNull(UUID userId);
}
