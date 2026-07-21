package com.moment.adapter.out.persistence.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    // 조건부 삭제 — 삭제된 행 수를 반환해 "한 번만 소비"를 보장한다(회전 원자성).
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity e WHERE e.tokenHash = :hash")
    int deleteByTokenHash(@Param("hash") String hash);

    void deleteAllByUserId(UUID userId);
}
