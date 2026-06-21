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

    Page<Room> findByHouseholdId(Long householdId, Pageable pageable);

    List<Room> findByHouseholdId(Long householdId);

    @Query("SELECT r FROM Room r WHERE r.household.id = :householdId AND " +
           "LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Room> findByHouseholdIdAndKeyword(@Param("householdId") Long householdId,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.id = :id AND r.household.createdBy.id = :userId")
    Optional<Room> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE r.id = :id AND r.household.createdBy.id = :userId")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
