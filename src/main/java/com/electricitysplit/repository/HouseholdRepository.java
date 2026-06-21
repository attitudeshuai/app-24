package com.electricitysplit.repository;

import com.electricitysplit.entity.Household;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long> {

    Page<Household> findByCreatedById(Long userId, Pageable pageable);

    @Query("SELECT h FROM Household h WHERE h.createdBy.id = :userId AND " +
           "(LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(h.address) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Household> findByCreatedByIdAndKeyword(@Param("userId") Long userId,
                                                @Param("keyword") String keyword,
                                                Pageable pageable);

    Optional<Household> findByIdAndCreatedById(Long id, Long userId);

    boolean existsByIdAndCreatedById(Long id, Long userId);
}
