package com.electricitysplit.repository;

import com.electricitysplit.entity.MeterReading;
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
public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    Page<MeterReading> findByHouseholdId(Long householdId, Pageable pageable);

    List<MeterReading> findByHouseholdIdOrderByReadingDateDesc(Long householdId);

    @Query("SELECT m FROM MeterReading m WHERE m.household.id = :householdId AND " +
           "m.readingDate BETWEEN :startDate AND :endDate")
    Page<MeterReading> findByHouseholdIdAndDateRange(@Param("householdId") Long householdId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate,
                                                     Pageable pageable);

    @Query("SELECT m FROM MeterReading m WHERE m.id = :id AND m.household.createdBy.id = :userId")
    Optional<MeterReading> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(m) > 0 FROM MeterReading m WHERE m.id = :id AND m.household.createdBy.id = :userId")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT m FROM MeterReading m WHERE m.household.id = :householdId ORDER BY m.readingDate DESC, m.id DESC LIMIT 1")
    Optional<MeterReading> findLatestByHouseholdId(@Param("householdId") Long householdId);

    @Query("SELECT m FROM MeterReading m WHERE m.household.id = :householdId AND m.readingDate <= :readingDate AND m.id != :excludeId ORDER BY m.readingDate DESC, m.id DESC LIMIT 1")
    Optional<MeterReading> findLatestByHouseholdIdAndDateBefore(
            @Param("householdId") Long householdId,
            @Param("readingDate") LocalDate readingDate,
            @Param("excludeId") Long excludeId);

    @Query("SELECT m FROM MeterReading m WHERE m.household.id = :householdId AND m.id != :excludeId ORDER BY m.readingDate DESC, m.id DESC LIMIT 1")
    Optional<MeterReading> findLatestByHouseholdIdExcludeId(
            @Param("householdId") Long householdId,
            @Param("excludeId") Long excludeId);

    @Query("SELECT m FROM MeterReading m WHERE m.household.id = :householdId AND m.readingDate > :readingDate ORDER BY m.readingDate ASC, m.id ASC LIMIT 1")
    Optional<MeterReading> findEarliestByHouseholdIdAndDateAfter(
            @Param("householdId") Long householdId,
            @Param("readingDate") LocalDate readingDate);

    @Query("SELECT m FROM MeterReading m WHERE m.household.id = :householdId AND " +
           "m.readingDate BETWEEN :startDate AND :endDate ORDER BY m.readingDate DESC")
    List<MeterReading> findByHouseholdIdAndDateRange(@Param("householdId") Long householdId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    @Query("SELECT m FROM MeterReading m WHERE m.household.id = :householdId AND m.readingDate <= :date " +
           "ORDER BY m.readingDate DESC")
    List<MeterReading> findLatestByHouseholdIdAndDate(@Param("householdId") Long householdId,
                                                       @Param("date") LocalDate date,
                                                       Pageable pageable);
}
