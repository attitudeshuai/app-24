package com.electricitysplit.repository;

import com.electricitysplit.entity.ReconciliationReport;
import com.electricitysplit.entity.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, Long> {

    Optional<ReconciliationReport> findByReportMonth(LocalDate reportMonth);

    List<ReconciliationReport> findByStatusOrderByReportMonthDesc(ReconciliationStatus status);

    List<ReconciliationReport> findByReportMonthBetweenOrderByReportMonthDesc(LocalDate startMonth, LocalDate endMonth);

    boolean existsByReportMonth(LocalDate reportMonth);
}
