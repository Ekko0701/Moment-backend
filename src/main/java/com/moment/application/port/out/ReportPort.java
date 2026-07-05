package com.moment.application.port.out;

import com.moment.domain.safety.Report;

/**
 * 신고 저장소 포트 (아웃바운드).
 */
public interface ReportPort {

    Report save(Report report);
}
