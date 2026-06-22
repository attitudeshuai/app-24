package com.electricitysplit.service;

import com.electricitysplit.dto.PaymentDto;
import com.electricitysplit.entity.*;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.BillItemRepository;
import com.electricitysplit.repository.BillRepository;
import com.electricitysplit.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final BillStateMachineService stateMachineService;
    private final NotificationService notificationService;

    @Transactional
    public PaymentDto.PaymentRecordResponse createPayment(User operator, PaymentDto.CreatePaymentRequest request) {
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new BusinessException("账单不存在"));

        if (bill.getStatus() != BillStatus.PENDING_PAYMENT
                && bill.getStatus() != BillStatus.OVERDUE
                && bill.getStatus() != BillStatus.PAID) {
            throw new BusinessException("当前账单状态不允许支付");
        }

        if (paymentRecordRepository.existsByTransactionNo(request.getTransactionNo())) {
            throw new BusinessException("支付流水号已存在，请勿重复提交");
        }

        BillItem billItem = null;
        BigDecimal amountDue;

        if (request.getBillItemId() != null) {
            billItem = billItemRepository.findById(request.getBillItemId())
                    .orElseThrow(() -> new BusinessException("账单明细不存在"));
            if (!billItem.getBill().getId().equals(bill.getId())) {
                throw new BusinessException("账单明细不属于该账单");
            }
            amountDue = billItem.getTotalDue();
        } else {
            amountDue = bill.getTotalAmount();
        }

        if (request.getPaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("支付金额必须大于0");
        }

        BigDecimal currentPaid = getNetPaidAmount(bill.getId(), billItem != null ? billItem.getId() : null);
        BigDecimal totalAfterPayment = currentPaid.add(request.getPaymentAmount());

        if (totalAfterPayment.compareTo(amountDue) > 0) {
            throw new BusinessException("支付金额超过应付金额，应付金额：" + amountDue + "元，已支付：" + currentPaid + "元");
        }

        LocalDateTime paymentTime = request.getPaymentTime() != null ? request.getPaymentTime() : LocalDateTime.now();

        PaymentRecord paymentRecord = PaymentRecord.builder()
                .bill(bill)
                .billItem(billItem)
                .paymentAmount(request.getPaymentAmount())
                .paymentMethod(request.getPaymentMethod())
                .transactionNo(request.getTransactionNo())
                .paymentTime(paymentTime)
                .operator(operator)
                .operatorName(operator.getUsername())
                .paymentType(PaymentType.PAYMENT)
                .status(PaymentStatus.SUCCESS)
                .remark(request.getRemark())
                .reconciled(false)
                .build();

        PaymentRecord saved = paymentRecordRepository.save(paymentRecord);

        if (billItem != null) {
            updateBillItemPaidStatus(billItem, totalAfterPayment);
        }

        BigDecimal billNetPaid = getNetPaidAmount(bill.getId(), null);
        if (billNetPaid.compareTo(bill.getTotalAmount()) >= 0
                && bill.getStatus() != BillStatus.PAID) {
            stateMachineService.transition(operator, bill, BillStatus.PAID, "支付完成，金额足额");
            log.info("账单 {} 已全额支付，状态更新为 PAID", bill.getId());
        }

        if (billItem != null && Boolean.FALSE.equals(billItem.getIsPaid())) {
            notificationService.notifyBillPaid(billItem);
        }

        log.info("支付记录创建成功：账单 {}, 金额 {}, 方式 {}, 流水号 {}",
                bill.getId(), request.getPaymentAmount(), request.getPaymentMethod(), request.getTransactionNo());

        return PaymentDto.PaymentRecordResponse.from(saved);
    }

    @Transactional
    public PaymentDto.PaymentRecordResponse cancelPayment(User operator, PaymentDto.CancelPaymentRequest request) {
        PaymentRecord originalPayment = paymentRecordRepository.findById(request.getPaymentRecordId())
                .orElseThrow(() -> new BusinessException("支付记录不存在"));

        if (originalPayment.getPaymentType() != PaymentType.PAYMENT) {
            throw new BusinessException("仅正向支付记录可取消");
        }

        if (originalPayment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException("仅成功的支付记录可取消");
        }

        if (originalPayment.getRelatedPayment() != null) {
            throw new BusinessException("该支付记录已被取消或退款");
        }

        Bill bill = originalPayment.getBill();
        BillItem billItem = originalPayment.getBillItem();

        String reverseTransactionNo = request.getTransactionNo() != null
                ? request.getTransactionNo()
                : "REV-" + originalPayment.getTransactionNo();

        if (paymentRecordRepository.existsByTransactionNo(reverseTransactionNo)) {
            throw new BusinessException("取消流水号已存在");
        }

        PaymentRecord reverseRecord = PaymentRecord.builder()
                .bill(bill)
                .billItem(billItem)
                .paymentAmount(originalPayment.getPaymentAmount())
                .paymentMethod(originalPayment.getPaymentMethod())
                .transactionNo(reverseTransactionNo)
                .paymentTime(LocalDateTime.now())
                .operator(operator)
                .operatorName(operator.getUsername())
                .paymentType(PaymentType.CANCEL)
                .relatedPayment(originalPayment)
                .status(PaymentStatus.SUCCESS)
                .remark(request.getReason() != null ? request.getReason() : "取消支付")
                .reconciled(false)
                .build();

        PaymentRecord savedReverse = paymentRecordRepository.save(reverseRecord);

        if (billItem != null) {
            BigDecimal remainingPaid = getNetPaidAmount(bill.getId(), billItem.getId());
            updateBillItemPaidStatus(billItem, remainingPaid);
        }

        BigDecimal billNetPaid = getNetPaidAmount(bill.getId(), null);
        if (billNetPaid.compareTo(bill.getTotalAmount()) < 0
                && bill.getStatus() == BillStatus.PAID) {
            stateMachineService.transition(operator, bill, BillStatus.PENDING_PAYMENT, "取消支付，金额不足，回退到待支付");
            log.info("账单 {} 因取消支付，金额不足，状态回退为 PENDING_PAYMENT", bill.getId());
        }

        log.info("支付取消成功：原支付记录 {}, 反向流水号 {}, 金额 {}",
                originalPayment.getId(), reverseTransactionNo, originalPayment.getPaymentAmount());

        return PaymentDto.PaymentRecordResponse.from(savedReverse);
    }

    @Transactional
    public PaymentDto.PaymentRecordResponse refundPayment(User operator, PaymentDto.RefundRequest request) {
        PaymentRecord originalPayment = paymentRecordRepository.findById(request.getPaymentRecordId())
                .orElseThrow(() -> new BusinessException("支付记录不存在"));

        if (originalPayment.getPaymentType() != PaymentType.PAYMENT) {
            throw new BusinessException("仅正向支付记录可退款");
        }

        if (originalPayment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException("仅成功的支付记录可退款");
        }

        if (originalPayment.getRelatedPayment() != null) {
            throw new BusinessException("该支付记录已被取消或退款");
        }

        Bill bill = originalPayment.getBill();
        BillItem billItem = originalPayment.getBillItem();

        BigDecimal refundAmount = request.getRefundAmount() != null
                ? request.getRefundAmount()
                : originalPayment.getPaymentAmount();

        if (refundAmount.compareTo(originalPayment.getPaymentAmount()) > 0) {
            throw new BusinessException("退款金额不能超过原支付金额");
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额必须大于0");
        }

        String refundTransactionNo = request.getTransactionNo() != null
                ? request.getTransactionNo()
                : "REF-" + originalPayment.getTransactionNo() + "-" + UUID.randomUUID().toString().substring(0, 8);

        if (paymentRecordRepository.existsByTransactionNo(refundTransactionNo)) {
            throw new BusinessException("退款流水号已存在");
        }

        PaymentRecord refundRecord = PaymentRecord.builder()
                .bill(bill)
                .billItem(billItem)
                .paymentAmount(refundAmount)
                .paymentMethod(request.getRefundMethod())
                .transactionNo(refundTransactionNo)
                .paymentTime(LocalDateTime.now())
                .operator(operator)
                .operatorName(operator.getUsername())
                .paymentType(PaymentType.REFUND)
                .relatedPayment(originalPayment)
                .status(PaymentStatus.SUCCESS)
                .remark(request.getReason() != null ? request.getReason() : "退款")
                .reconciled(false)
                .build();

        PaymentRecord savedRefund = paymentRecordRepository.save(refundRecord);

        if (billItem != null) {
            BigDecimal remainingPaid = getNetPaidAmount(bill.getId(), billItem.getId());
            updateBillItemPaidStatus(billItem, remainingPaid);
        }

        BigDecimal billNetPaid = getNetPaidAmount(bill.getId(), null);
        if (billNetPaid.compareTo(bill.getTotalAmount()) < 0
                && bill.getStatus() == BillStatus.PAID) {
            stateMachineService.transition(operator, bill, BillStatus.PENDING_PAYMENT, "退款后金额不足，回退到待支付");
            log.info("账单 {} 因退款，金额不足，状态回退为 PENDING_PAYMENT", bill.getId());
        }

        log.info("退款成功：原支付记录 {}, 退款流水号 {}, 退款金额 {}",
                originalPayment.getId(), refundTransactionNo, refundAmount);

        return PaymentDto.PaymentRecordResponse.from(savedRefund);
    }

    public List<PaymentDto.PaymentRecordResponse> getPaymentRecordsByBill(Long billId) {
        List<PaymentRecord> records = paymentRecordRepository.findByBillIdOrderByPaymentTimeDesc(billId);
        return records.stream()
                .map(PaymentDto.PaymentRecordResponse::from)
                .collect(Collectors.toList());
    }

    public List<PaymentDto.PaymentRecordResponse> getPaymentRecordsByBillItem(Long billItemId) {
        List<PaymentRecord> records = paymentRecordRepository.findByBillItemIdOrderByPaymentTimeDesc(billItemId);
        return records.stream()
                .map(PaymentDto.PaymentRecordResponse::from)
                .collect(Collectors.toList());
    }

    public PaymentDto.PaymentSummaryResponse getPaymentSummary(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BusinessException("账单不存在"));

        BigDecimal totalPaid = paymentRecordRepository.sumPaymentAmountByBillIdAndTypeAndStatus(
                billId, PaymentType.PAYMENT, PaymentStatus.SUCCESS);
        BigDecimal totalRefunded = paymentRecordRepository.sumPaymentAmountByBillIdAndTypeAndStatus(
                billId, PaymentType.REFUND, PaymentStatus.SUCCESS);
        BigDecimal totalCancelled = paymentRecordRepository.sumPaymentAmountByBillIdAndTypeAndStatus(
                billId, PaymentType.CANCEL, PaymentStatus.SUCCESS);

        BigDecimal netPaid = totalPaid.subtract(totalRefunded).subtract(totalCancelled);
        BigDecimal remaining = bill.getTotalAmount().subtract(netPaid);

        return PaymentDto.PaymentSummaryResponse.builder()
                .billId(billId)
                .totalDue(bill.getTotalAmount())
                .totalPaid(totalPaid)
                .totalRefunded(totalRefunded.add(totalCancelled))
                .netPaid(netPaid)
                .remainingAmount(remaining.max(BigDecimal.ZERO))
                .isFullyPaid(netPaid.compareTo(bill.getTotalAmount()) >= 0)
                .build();
    }

    public BigDecimal getNetPaidAmount(Long billId, Long billItemId) {
        if (billItemId != null) {
            BigDecimal payments = paymentRecordRepository.sumPaymentAmountByBillItemIdAndTypeAndStatus(
                    billItemId, PaymentType.PAYMENT, PaymentStatus.SUCCESS);
            BigDecimal refunds = paymentRecordRepository.sumPaymentAmountByBillItemIdAndTypeAndStatus(
                    billItemId, PaymentType.REFUND, PaymentStatus.SUCCESS);
            BigDecimal cancels = paymentRecordRepository.sumPaymentAmountByBillItemIdAndTypeAndStatus(
                    billItemId, PaymentType.CANCEL, PaymentStatus.SUCCESS);
            return payments.subtract(refunds).subtract(cancels);
        } else {
            BigDecimal payments = paymentRecordRepository.sumPaymentAmountByBillIdAndTypeAndStatus(
                    billId, PaymentType.PAYMENT, PaymentStatus.SUCCESS);
            BigDecimal refunds = paymentRecordRepository.sumPaymentAmountByBillIdAndTypeAndStatus(
                    billId, PaymentType.REFUND, PaymentStatus.SUCCESS);
            BigDecimal cancels = paymentRecordRepository.sumPaymentAmountByBillIdAndTypeAndStatus(
                    billId, PaymentType.CANCEL, PaymentStatus.SUCCESS);
            return payments.subtract(refunds).subtract(cancels);
        }
    }

    private void updateBillItemPaidStatus(BillItem billItem, BigDecimal paidAmount) {
        boolean isFullyPaid = paidAmount.compareTo(billItem.getTotalDue()) >= 0;
        billItem.setIsPaid(isFullyPaid);
        if (isFullyPaid && billItem.getPaidAt() == null) {
            billItem.setPaidAt(LocalDateTime.now());
        } else if (!isFullyPaid) {
            billItem.setPaidAt(null);
        }
        billItemRepository.save(billItem);
    }
}
