package com.electricitysplit.repository;

import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    Page<Bill> findByHouseholdId(Long householdId, Pageable pageable);

    List<Bill> findByHouseholdIdOrderByPeriodEndDesc(Long householdId);

    Page<Bill> findByHouseholdIdAndStatus(Long householdId, BillStatus status, Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE b.household.id = :householdId AND " +
           "b.periodEnd BETWEEN :startDate AND :endDate")
    Page<Bill> findByHouseholdIdAndDateRange(@Param("householdId") Long householdId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate,
                                             Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE b.id = :id AND b.household.createdBy.id = :userId")
    Optional<Bill> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(b) > 0 FROM Bill b WHERE b.id = :id AND b.household.createdBy.id = :userId")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.household.createdBy.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.household.createdBy.id = :userId AND b.status = :status")
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") BillStatus status);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.household.createdBy.id = :userId")
    java.math.BigDecimal sumTotalAmountByUserId(@Param("userId") Long userId);

    @Query("SELECT b FROM Bill b WHERE b.status = :status AND (b.dueDate IS NULL OR b.dueDate < :cutoffDate)")
    List<Bill> findOverdueCandidates(@Param("status") BillStatus status, @Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT b FROM Bill b WHERE b.status = :status AND b.periodEnd < :cutoffDate AND b.dueDate IS NULL")
    List<Bill> findBillsWithoutDueDate(@Param("status") BillStatus status, @Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT b FROM Bill b WHERE b.status IN :statuses AND b.household.createdBy.id = :userId")
    List<Bill> findByUserIdAndStatusIn(@Param("userId") Long userId, @Param("statuses") List<BillStatus> statuses);

    @Modifying
    @Query("UPDATE Bill b SET b.ruleVersion = :ruleVersion WHERE b.ruleVersion IS NULL")
    int migrateNullRuleVersion(@Param("ruleVersion") String ruleVersion);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.ruleVersion IS NULL OR b.ruleVersion != :currentVersion")
    long countBillsWithDifferentRuleVersion(@Param("currentVersion") String currentVersion);

    @Query("SELECT DISTINCT b FROM Bill b JOIN BillItem bi ON b.id = bi.bill.id " +
           "WHERE bi.room.id = :roomId AND b.status != :status AND b.household.createdBy.id = :userId")
    List<Bill> findUnpaidBillsByRoomId(@Param("roomId") Long roomId,
                                       @Param("status") BillStatus status,
                                       @Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT b) FROM Bill b JOIN BillItem bi ON b.id = bi.bill.id " +
           "WHERE bi.room.id = :roomId AND b.status != :status AND b.household.createdBy.id = :userId")
    long countUnpaidBillsByRoomId(@Param("roomId") Long roomId,
                                  @Param("status") BillStatus status,
                                  @Param("userId") Long userId);

    @Query("SELECT DISTINCT b FROM Bill b JOIN BillItem bi ON b.id = bi.bill.id " +
           "WHERE bi.room.id = :roomId AND b.household.createdBy.id = :userId ORDER BY b.periodEnd DESC")
    List<Bill> findAllBillsByRoomId(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("SELECT COUNT(b) > 0 FROM Bill b WHERE b.household.id = :householdId AND " +
           "b.periodStart = :periodStart AND b.periodEnd = :periodEnd")
    boolean existsByHouseholdIdAndPeriod(@Param("householdId") Long householdId,
                                          @Param("periodStart") LocalDate periodStart,
                                          @Param("periodEnd") LocalDate periodEnd);

    @Query("SELECT b FROM Bill b WHERE b.household.id = :householdId AND b.periodEnd <= :periodEnd " +
           "ORDER BY b.periodEnd DESC")
    List<Bill> findRecentBillsByHouseholdId(@Param("householdId") Long householdId,
                                             @Param("periodEnd") LocalDate periodEnd,
                                             Pageable pageable);
}
