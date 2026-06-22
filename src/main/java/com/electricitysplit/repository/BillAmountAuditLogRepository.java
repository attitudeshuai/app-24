package com.electricitysplit.repository;

import com.electricitysplit.entity.BillAmountAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillAmountAuditLogRepository extends JpaRepository<BillAmountAuditLog, Long> {

    List<BillAmountAuditLog> findByBillIdOrderByCreatedAtDesc(Long billId);

    @Query("SELECT l FROM BillAmountAuditLog l WHERE l.isValid = false AND l.createdAt >= :since ORDER BY l.createdAt DESC")
    List<BillAmountAuditLog> findInvalidLogsSince(@Param("since") LocalDateTime since);

    long countByBillIdAndIsValidFalse(Long billId);
}
