package com.electricitysplit.repository;

import com.electricitysplit.entity.MeterReadingCorrection;
import com.electricitysplit.entity.MeterReadingCorrectionStatus;
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
public interface MeterReadingCorrectionRepository extends JpaRepository<MeterReadingCorrection, Long> {

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.id = :id AND c.household.createdBy.id = :userId")
    Optional<MeterReadingCorrection> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(c) > 0 FROM MeterReadingCorrection c WHERE c.id = :id AND c.household.createdBy.id = :userId")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.household.id = :householdId ORDER BY c.createdAt DESC")
    Page<MeterReadingCorrection> findByHouseholdId(@Param("householdId") Long householdId, Pageable pageable);

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.household.id = :householdId AND c.status = :status ORDER BY c.createdAt DESC")
    List<MeterReadingCorrection> findByHouseholdIdAndStatus(
            @Param("householdId") Long householdId,
            @Param("status") MeterReadingCorrectionStatus status);

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.status = :status ORDER BY c.createdAt DESC")
    Page<MeterReadingCorrection> findByStatus(@Param("status") MeterReadingCorrectionStatus status, Pageable pageable);

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.household.createdBy.id = :userId ORDER BY c.createdAt DESC")
    Page<MeterReadingCorrection> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.household.id = :householdId " +
           "AND c.readingDate BETWEEN :startDate AND :endDate ORDER BY c.createdAt DESC")
    List<MeterReadingCorrection> findByHouseholdIdAndDateRange(
            @Param("householdId") Long householdId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.originalReading.id = :readingId AND c.status = 'APPROVED'")
    Optional<MeterReadingCorrection> findApprovedByOriginalReadingId(@Param("readingId") Long readingId);

    @Query("SELECT c FROM MeterReadingCorrection c WHERE c.status = 'APPROVED' AND c.billsRecalculated = false")
    List<MeterReadingCorrection> findApprovedButNotRecalculated();
}
