package com.moment.adapter.out.persistence.space;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpaceJpaRepository extends JpaRepository<SpaceEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM SpaceEntity e WHERE e.id = :id")
    Optional<SpaceEntity> findByIdForUpdate(@Param("id") UUID id);
}
