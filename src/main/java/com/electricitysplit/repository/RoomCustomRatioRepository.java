package com.electricitysplit.repository;

import com.electricitysplit.entity.RoomCustomRatio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomCustomRatioRepository extends JpaRepository<RoomCustomRatio, Long> {

    List<RoomCustomRatio> findByAllocationRuleId(Long allocationRuleId);

    @Query("SELECT rcr FROM RoomCustomRatio rcr WHERE rcr.allocationRule.id = :allocationRuleId AND rcr.room.id = :roomId")
    Optional<RoomCustomRatio> findByAllocationRuleIdAndRoomId(@Param("allocationRuleId") Long allocationRuleId, @Param("roomId") Long roomId);

    @Query("SELECT rcr FROM RoomCustomRatio rcr WHERE rcr.id = :id AND rcr.allocationRule.household.createdBy.id = :userId")
    Optional<RoomCustomRatio> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
