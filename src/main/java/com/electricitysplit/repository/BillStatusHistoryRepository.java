package com.electricitysplit.repository;

import com.electricitysplit.entity.BillStatus;
import com.electricitysplit.entity.BillStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillStatusHistoryRepository extends JpaRepository<BillStatusHistory, Long> {

    List<BillStatusHistory> findByBillIdOrderByOperatedAtDesc(Long billId);

    Optional<BillStatusHistory> findTopByBillIdOrderByOperatedAtDesc(Long billId);

    @Query("SELECT h FROM BillStatusHistory h WHERE h.bill.id = :billId AND h.toStatus = :status ORDER BY h.operatedAt DESC")
    List<BillStatusHistory> findByBillIdAndToStatus(@Param("billId") Long billId, @Param("status") BillStatus status);

    @Query("SELECT h FROM BillStatusHistory h WHERE h.ruleVersion != :currentVersion AND h.operatedAt > :since ORDER BY h.operatedAt DESC")
    List<BillStatusHistory> findAbnormalTransitions(@Param("currentVersion") String currentVersion, @Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT h.bill.id FROM BillStatusHistory h WHERE h.operatedAt BETWEEN :start AND :end AND h.isAuto = false")
    List<Long> findManuallyTransitionedBillIds(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
