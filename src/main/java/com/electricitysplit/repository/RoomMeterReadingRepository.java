package com.electricitysplit.repository;

import com.electricitysplit.entity.RoomMeterReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMeterReadingRepository extends JpaRepository<RoomMeterReading, Long> {

    List<RoomMeterReading> findByMeterReadingId(Long meterReadingId);

    List<RoomMeterReading> findByRoomId(Long roomId);

    @Query("SELECT rmr FROM RoomMeterReading rmr WHERE rmr.meterReading.id = :meterReadingId AND rmr.room.id = :roomId")
    Optional<RoomMeterReading> findByMeterReadingIdAndRoomId(@Param("meterReadingId") Long meterReadingId, @Param("roomId") Long roomId);

    @Query("SELECT rmr FROM RoomMeterReading rmr WHERE rmr.room.id = :roomId AND rmr.meterReading.readingDate <= :endDate ORDER BY rmr.meterReading.readingDate DESC")
    List<RoomMeterReading> findLatestByRoomIdAndDate(@Param("roomId") Long roomId, @Param("endDate") LocalDate endDate);

    @Query("SELECT rmr FROM RoomMeterReading rmr WHERE rmr.id = :id AND rmr.meterReading.household.createdBy.id = :userId")
    Optional<RoomMeterReading> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
