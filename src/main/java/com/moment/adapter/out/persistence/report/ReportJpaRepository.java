package com.moment.adapter.out.persistence.report;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReportJpaRepository extends JpaRepository<ReportEntity, UUID> {
}
