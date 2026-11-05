package com.gachiga.user;

import org.springframework.data.jpa.repository.JpaRepository;

/** {@link Report} 저장. 누적 횟수는 {@code User.reportCount} 로 관리하므로 별도 집계 쿼리는 없다. */
public interface ReportRepository extends JpaRepository<Report, Long> {}
