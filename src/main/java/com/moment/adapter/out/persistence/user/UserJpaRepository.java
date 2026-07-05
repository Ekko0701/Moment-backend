package com.moment.adapter.out.persistence.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByAppleSub(String appleSub);

    Optional<UserEntity> findByHandle(String handle);

    boolean existsByHandle(String handle);
}
