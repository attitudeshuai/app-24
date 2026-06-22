package com.electricitysplit.repository;

import com.electricitysplit.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("SELECT r FROM Room r WHERE r.household.id = :householdId AND r.isDeleted = false")
    Page<Room> findByHouseholdId(@Param("householdId") Long householdId, Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.household.id = :householdId AND r.isDeleted = false")
    List<Room> findByHouseholdId(@Param("householdId") Long householdId);

    @Query("SELECT r FROM Room r WHERE r.household.id = :householdId AND r.isDeleted = false AND " +
           "LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Room> findByHouseholdIdAndKeyword(@Param("householdId") Long householdId,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.id = :id AND r.household.createdBy.id = :userId AND r.isDeleted = false")
    Optional<Room> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE r.id = :id AND r.household.createdBy.id = :userId AND r.isDeleted = false")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT r FROM Room r WHERE r.id = :id AND r.household.createdBy.id = :userId")
    Optional<Room> findByIdAndUserIdIncludeDeleted(@Param("id") Long id, @Param("userId") Long userId);
}
