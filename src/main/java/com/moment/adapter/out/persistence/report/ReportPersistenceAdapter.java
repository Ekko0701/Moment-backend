package com.moment.adapter.out.persistence.report;

import com.moment.application.port.out.ReportPort;
import com.moment.domain.safety.Report;
import org.springframework.stereotype.Component;

@Component
class ReportPersistenceAdapter implements ReportPort {

    private final ReportJpaRepository jpaRepository;

    ReportPersistenceAdapter(ReportJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Report save(Report report) {
        ReportEntity entity = ReportEntity.fromDomain(report);
        return jpaRepository.save(entity).toDomain();
    }
}
