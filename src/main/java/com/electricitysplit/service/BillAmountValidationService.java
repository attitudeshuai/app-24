package com.electricitysplit.service;

import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillAmountAuditLog;
import com.electricitysplit.entity.BillItem;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.BillAmountAuditLogRepository;
import com.electricitysplit.repository.BillItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillAmountValidationService {

    private final BillAmountAuditLogRepository auditLogRepository;
    private final BillItemRepository billItemRepository;
    private final NotificationService notificationService;

    private static final BigDecimal MAX_ROUNDING_DIFFERENCE = new BigDecimal("0.05");

    @Transactional
    public List<BillItem> validateAndAdjust(Bill bill, List<BillItem> items, User operator) {
        String beforeDetails = serializeItems(items);
        BigDecimal beforeSum = sumItems(items);
        BigDecimal billTotal = bill.getTotalAmount();
        BigDecimal difference = billTotal.subtract(beforeSum);

        boolean hasRoundingAdjustment = false;
        String errorMessage = null;
        boolean isValid = true;

        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            if (isWithinRoundingTolerance(difference)) {
                applyRoundingAdjustment(items, difference);
                hasRoundingAdjustment = true;
                log.info("账单 {} 金额校验：检测到尾差 {} 元，已分摊到最后一笔明细", bill.getId(), difference);
            } else {
                isValid = false;
                errorMessage = String.format(
                    "金额校验失败：明细合计 %.2f 元与账单总额 %.2f 元差额 %.2f 元，超出尾差容限 %.2f 元，请检查分摊规则",
                    beforeSum, billTotal, difference, MAX_ROUNDING_DIFFERENCE
                );
                log.warn("账单 {} 金额校验失败：{}", bill.getId(), errorMessage);
            }
        }

        BigDecimal afterSum = sumItems(items);
        String afterDetails = serializeItems(items);

        if (isValid && afterSum.compareTo(billTotal) != 0) {
            isValid = false;
            errorMessage = String.format(
                "金额校验失败：调整后明细合计 %.2f 元仍不等于账单总额 %.2f 元，请检查分摊规则",
                afterSum, billTotal
            );
            log.error("账单 {} 金额校验严重失败：{}", bill.getId(), errorMessage);
        }

        saveAuditLog(bill, beforeDetails, afterDetails, billTotal, beforeSum, afterSum,
                difference, hasRoundingAdjustment, isValid, operator, errorMessage);

        if (!isValid) {
            notificationService.notifyAdmin(
                bill,
                "【异常告警】账单金额校验失败",
                String.format(
                    "账单ID：%d\n账单期间：%s 至 %s\n账单总额：%.2f 元\n明细调整前合计：%.2f 元\n明细调整后合计：%.2f 元\n差额：%.2f 元\n错误信息：%s\n\n请及时检查分摊规则设置。",
                    bill.getId(),
                    bill.getPeriodStart(),
                    bill.getPeriodEnd(),
                    billTotal,
                    beforeSum,
                    afterSum,
                    difference,
                    errorMessage
                )
            );
            throw new BusinessException(errorMessage);
        }

        return items;
    }

    @Transactional(readOnly = true)
    public void validateForPaymentTransition(Bill bill) {
        List<BillItem> items = billItemRepository.findByBillId(bill.getId());
        if (items.isEmpty()) {
            throw new BusinessException("账单没有明细数据，无法进入待支付状态，请先生成分摊明细");
        }

        BigDecimal sum = sumItems(items);
        if (sum.compareTo(bill.getTotalAmount()) != 0) {
            String errorMessage = String.format(
                "金额校验失败：明细合计 %.2f 元与账单总额 %.2f 元不一致，差额 %.2f 元，请检查分摊规则后重试",
                sum, bill.getTotalAmount(), bill.getTotalAmount().subtract(sum)
            );

            saveAuditLog(bill, serializeItems(items), serializeItems(items),
                    bill.getTotalAmount(), sum, sum,
                    bill.getTotalAmount().subtract(sum), false, false, null, errorMessage);

            notificationService.notifyAdmin(
                bill,
                "【异常告警】账单进入待支付前校验失败",
                String.format(
                    "账单ID：%d\n账单期间：%s 至 %s\n账单总额：%.2f 元\n明细合计：%.2f 元\n差额：%.2f 元\n错误信息：%s\n\n请检查并修正分摊规则。",
                    bill.getId(),
                    bill.getPeriodStart(),
                    bill.getPeriodEnd(),
                    bill.getTotalAmount(),
                    sum,
                    bill.getTotalAmount().subtract(sum),
                    errorMessage
                )
            );

            throw new BusinessException(errorMessage);
        }
    }

    private boolean isWithinRoundingTolerance(BigDecimal difference) {
        return difference.abs().compareTo(MAX_ROUNDING_DIFFERENCE) <= 0;
    }

    private void applyRoundingAdjustment(List<BillItem> items, BigDecimal difference) {
        if (items.isEmpty()) {
            return;
        }

        BillItem lastItem = items.get(items.size() - 1);
        BigDecimal originalTotalDue = lastItem.getTotalDue();
        BigDecimal newTotalDue = originalTotalDue.add(difference).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualAdjustment = newTotalDue.subtract(originalTotalDue);

        lastItem.setTotalDue(newTotalDue);
        lastItem.setHasRoundingAdjustment(true);
        lastItem.setRoundingAdjustmentAmount(actualAdjustment);

        String existingDetails = lastItem.getCalculationDetails() != null ? lastItem.getCalculationDetails() : "";
        if (!existingDetails.contains("尾差调整")) {
            lastItem.setCalculationDetails(
                existingDetails + String.format("尾差调整：%+.2f元", actualAdjustment)
            );
        }
    }

    private BigDecimal sumItems(List<BillItem> items) {
        return items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String serializeItems(List<BillItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            BillItem item = items.get(i);
            sb.append(String.format(
                "[%d]房间=%s,基础=%.2f,空调=%.2f,公共=%.2f,合计=%.2f",
                i,
                item.getRoom() != null ? item.getRoom().getName() : "N/A",
                item.getBaseShare(),
                item.getAcShare(),
                item.getPublicShare(),
                item.getTotalDue()
            ));
            if (i < items.size() - 1) {
                sb.append(" | ");
            }
        }
        return sb.toString();
    }

    private void saveAuditLog(Bill bill, String beforeDetails, String afterDetails,
                              BigDecimal billTotalAmount, BigDecimal beforeSum, BigDecimal afterSum,
                              BigDecimal difference, boolean hasRoundingAdjustment, boolean isValid,
                              User operator, String errorMessage) {
        BillAmountAuditLog auditLog = BillAmountAuditLog.builder()
                .bill(bill)
                .beforeAdjustmentDetails(beforeDetails)
                .afterAdjustmentDetails(afterDetails)
                .billTotalAmount(billTotalAmount)
                .beforeSum(beforeSum)
                .afterSum(afterSum)
                .difference(difference)
                .hasRoundingAdjustment(hasRoundingAdjustment)
                .isValid(isValid)
                .operator(operator)
                .operatorName(operator != null ? operator.getUsername() : "SYSTEM")
                .errorMessage(errorMessage)
                .build();
        auditLogRepository.save(auditLog);

        log.info(
            "账单金额审计日志已记录：账单ID={}, 校验结果={}, 差额={}, 尾差调整={}",
            bill.getId(), isValid ? "通过" : "失败", difference, hasRoundingAdjustment
        );
    }

    public List<BillAmountAuditLog> getAuditLogsForBill(Long billId) {
        return auditLogRepository.findByBillIdOrderByCreatedAtDesc(billId);
    }
}
