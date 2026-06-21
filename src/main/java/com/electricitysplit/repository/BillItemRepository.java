package com.electricitysplit.repository;

import com.electricitysplit.entity.BillItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillItemRepository extends JpaRepository<BillItem, Long> {

    Page<BillItem> findByBillId(Long billId, Pageable pageable);

    List<BillItem> findByBillId(Long billId);

    List<BillItem> findByRoomId(Long roomId);

    @Query("SELECT bi FROM BillItem bi WHERE bi.id = :id AND bi.bill.household.createdBy.id = :userId")
    Optional<BillItem> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(bi) > 0 FROM BillItem bi WHERE bi.id = :id AND bi.bill.household.createdBy.id = :userId")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
