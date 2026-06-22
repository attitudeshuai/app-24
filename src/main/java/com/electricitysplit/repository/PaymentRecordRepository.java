package com.electricitysplit.repository;

import com.electricitysplit.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByBillIdOrderByPaymentTimeDesc(Long billId);

    List<PaymentRecord> findByBillItemIdOrderByPaymentTimeDesc(Long billItemId);

    Optional<PaymentRecord> findByTransactionNo(String transactionNo);

    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.bill.id = :billId AND pr.paymentType = :paymentType AND pr.status = :status ORDER BY pr.paymentTime DESC")
    List<PaymentRecord> findByBillIdAndPaymentTypeAndStatus(
            @Param("billId") Long billId,
            @Param("paymentType") PaymentType paymentType,
            @Param("status") PaymentStatus status);

    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.billItem.id = :billItemId AND pr.paymentType = :paymentType AND pr.status = :status ORDER BY pr.paymentTime DESC")
    List<PaymentRecord> findByBillItemIdAndPaymentTypeAndStatus(
            @Param("billItemId") Long billItemId,
            @Param("paymentType") PaymentType paymentType,
            @Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(pr.paymentAmount), 0) FROM PaymentRecord pr WHERE pr.bill.id = :billId AND pr.paymentType = :paymentType AND pr.status = :status")
    BigDecimal sumPaymentAmountByBillIdAndTypeAndStatus(
            @Param("billId") Long billId,
            @Param("paymentType") PaymentType paymentType,
            @Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(pr.paymentAmount), 0) FROM PaymentRecord pr WHERE pr.billItem.id = :billItemId AND pr.paymentType = :paymentType AND pr.status = :status")
    BigDecimal sumPaymentAmountByBillItemIdAndTypeAndStatus(
            @Param("billItemId") Long billItemId,
            @Param("paymentType") PaymentType paymentType,
            @Param("status") PaymentStatus status);

    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.paymentTime BETWEEN :startTime AND :endTime AND pr.status = :status AND pr.reconciled = :reconciled")
    List<PaymentRecord> findByPaymentTimeBetweenAndStatusAndReconciled(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("status") PaymentStatus status,
            @Param("reconciled") Boolean reconciled);

    @Query("SELECT COALESCE(SUM(pr.paymentAmount), 0) FROM PaymentRecord pr WHERE pr.paymentTime BETWEEN :startTime AND :endTime AND pr.paymentType = :paymentType AND pr.status = :status")
    BigDecimal sumPaymentAmountByTimeRangeAndTypeAndStatus(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("paymentType") PaymentType paymentType,
            @Param("status") PaymentStatus status);

    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.paymentTime BETWEEN :startTime AND :endTime AND pr.paymentType = :paymentType AND pr.status = :status ORDER BY pr.paymentTime ASC")
    List<PaymentRecord> findByTimeRangeAndTypeAndStatus(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("paymentType") PaymentType paymentType,
            @Param("status") PaymentStatus status);

    boolean existsByTransactionNo(String transactionNo);
}
