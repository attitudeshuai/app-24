package com.electricitysplit.repository;

import com.electricitysplit.entity.AllocationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRuleRepository extends JpaRepository<AllocationRule, Long> {

    List<AllocationRule> findByHouseholdId(Long householdId);

    @Query("SELECT ar FROM AllocationRule ar WHERE ar.household.id = :householdId AND ar.isActive = true")
    Optional<AllocationRule> findActiveByHouseholdId(@Param("householdId") Long householdId);

    @Query("SELECT ar FROM AllocationRule ar WHERE ar.id = :id AND ar.household.createdBy.id = :userId")
    Optional<AllocationRule> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
