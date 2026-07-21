package com.moment.adapter.out.persistence.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    // id 오름차순으로 행을 잠가(PESSIMISTIC_WRITE) 트랜잭션 간 잠금 순서를 일정하게 유지 → 데드락 회피.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id IN :ids ORDER BY u.id")
    List<UserEntity> lockAllByIdOrdered(@Param("ids") List<UUID> ids);

    Optional<UserEntity> findByAppleSub(String appleSub);

    Optional<UserEntity> findByHandle(String handle);

    boolean existsByHandle(String handle);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
