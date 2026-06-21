package com.electricitysplit.repository;

import com.electricitysplit.entity.Bill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Page<Bill> findByHouseholdIdAndStatus(Long householdId, Bill.BillStatus status, Pageable pageable);

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
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Bill.BillStatus status);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.household.createdBy.id = :userId")
    java.math.BigDecimal sumTotalAmountByUserId(@Param("userId") Long userId);
}
