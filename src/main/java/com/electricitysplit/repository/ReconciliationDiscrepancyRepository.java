package com.electricitysplit.repository;

import com.electricitysplit.entity.ReconciliationDiscrepancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancy, Long> {

    List<ReconciliationDiscrepancy> findByReportIdOrderByCreatedAtDesc(Long reportId);

    List<ReconciliationDiscrepancy> findByReportIdAndIsResolvedOrderByCreatedAtDesc(Long reportId, Boolean isResolved);

    long countByReportIdAndIsResolved(Long reportId, Boolean isResolved);
}
