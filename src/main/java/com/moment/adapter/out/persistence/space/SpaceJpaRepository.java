package com.moment.adapter.out.persistence.space;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpaceJpaRepository extends JpaRepository<SpaceEntity, UUID> {
}
