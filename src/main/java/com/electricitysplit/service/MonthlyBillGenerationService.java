package com.electricitysplit.service;

import com.electricitysplit.config.BillStateMachineConfig;
import com.electricitysplit.config.SchedulerConfig;
import com.electricitysplit.entity.*;
import com.electricitysplit.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyBillGenerationService {

    private final HouseholdRepository householdRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final RoomMeterReadingRepository roomMeterReadingRepository;
    private final BillStatusHistoryRepository billStatusHistoryRepository;
    private final UserRepository userRepository;

    private final BillSplitService billSplitService;
    private final BillAmountValidationService amountValidationService;
    private final BillStateMachineService stateMachineService;
    private final NotificationService notificationService;
    private final MeterReadingEstimationService estimationService;
    private final SchedulerConfig schedulerConfig;
    private final BillStateMachineConfig stateMachineConfig;

    public static class GenerationResult {
        private final int successCount;
        private final int failureCount;
        private final int skippedCount;
        private final List<String> details;
        private final List<String> errors;

        public GenerationResult(int successCount, int failureCount, int skippedCount,
                                List<String> details, List<String> errors) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.skippedCount = skippedCount;
            this.details = details;
            this.errors = errors;
        }

        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public int getSkippedCount() { return skippedCount; }
        public List<String> getDetails() { return details; }
        public List<String> getErrors() { return errors; }
    }

    @Transactional
    public GenerationResult generateMonthlyBills(YearMonth billingPeriod) {
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;
        List<String> details = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        LocalDate periodStart = billingPeriod.atDay(1);
        LocalDate periodEnd = billingPeriod.atEndOfMonth();
        String periodStr = billingPeriod.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        log.info("开始生成 {} 月度账单，账期: {} 至 {}", periodStr, periodStart, periodEnd);

        List<Household> households = householdRepository.findAll();
        log.info("共找到 {} 个住户需要处理", households.size());

        User systemUser = getSystemUser();

        for (Household household : households) {
            try {
                GenerationResultForHousehold result = generateBillForHousehold(
                        household, billingPeriod, periodStart, periodEnd, systemUser);

                switch (result.getStatus()) {
                    case SUCCESS:
                        successCount++;
                        details.add(result.getMessage());
                        log.info(result.getMessage());
                        break;
                    case SKIPPED:
                        skippedCount++;
                        details.add(result.getMessage());
                        log.info(result.getMessage());
                        break;
                    case FAILED:
                        failureCount++;
                        errors.add(result.getMessage());
                        log.error(result.getMessage());
                        break;
                }
            } catch (Exception e) {
                failureCount++;
                String errorMsg = String.format("住户 [%s](ID: %d) 账单生成失败: %s",
                        household.getName(), household.getId(), e.getMessage());
                errors.add(errorMsg);
                log.error(errorMsg, e);
            }
        }

        String summary = String.format("月度账单生成完成 - 成功: %d, 失败: %d, 跳过: %d, 账期: %s",
                successCount, failureCount, skippedCount, periodStr);
        log.info(summary);
        details.add(0, summary);

        return new GenerationResult(successCount, failureCount, skippedCount, details, errors);
    }

    private GenerationResultForHousehold generateBillForHousehold(
            Household household,
            YearMonth billingPeriod,
            LocalDate periodStart,
            LocalDate periodEnd,
            User systemUser) {

        if (billRepository.existsByHouseholdIdAndPeriod(household.getId(), periodStart, periodEnd)) {
            return new GenerationResultForHousehold(
                    GenerationStatus.SKIPPED,
                    String.format("住户 [%s](ID: %d) 已存在 %s 账期的账单，跳过",
                            household.getName(), household.getId(), billingPeriod)
            );
        }

        MeterReadingEstimationService.EstimationResult estimationResult =
                estimationService.getOrEstimateMeterReading(household, billingPeriod, periodStart, periodEnd);

        List<String> estimationLogs = estimationResult.getEstimationLogs();
        estimationLogs.forEach(logLine -> log.info("[{}] {}", household.getName(), logLine));

        if (estimationResult.isSkipped()) {
            return new GenerationResultForHousehold(
                    GenerationStatus.SKIPPED,
                    String.format("住户 [%s](ID: %d) 账单生成跳过: %s",
                            household.getName(), household.getId(), estimationResult.getSkipReason())
            );
        }

        MeterReading meterReading = estimationResult.getMeterReading();
        Map<Long, RoomMeterReading> roomReadings = estimationResult.getRoomReadings();

        if (meterReading == null) {
            return new GenerationResultForHousehold(
                    GenerationStatus.FAILED,
                    String.format("住户 [%s](ID: %d) 无法获取或估算电表读数",
                            household.getName(), household.getId())
            );
        }

        if (meterReading.getId() == null) {
            meterReading = meterReadingRepository.save(meterReading);
            log.info("已保存估算的电表读数 (ID: {})", meterReading.getId());

            for (RoomMeterReading roomReading : roomReadings.values()) {
                roomReading.setMeterReading(meterReading);
                roomMeterReadingRepository.save(roomReading);
            }
            log.info("已保存 {} 个房间的估算电表读数", roomReadings.size());
        }

        BigDecimal totalAmount = meterReading.getAmount();

        Bill bill = Bill.builder()
                .household(household)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalAmount(totalAmount)
                .dueDate(periodEnd.plusDays(stateMachineConfig.getOverdueDaysAfterPeriodEnd()))
                .build();

        bill.setStatusInternal(BillStatus.PENDING_CONFIRMATION);
        bill.setRuleVersionInternal(stateMachineConfig.getCurrentRuleVersion());

        Bill savedBill = billRepository.save(bill);
        log.info("已创建账单 (ID: {})，住户: {}, 账期: {} 至 {}, 总金额: {} 元",
                savedBill.getId(), household.getName(), periodStart, periodEnd, totalAmount);

        billStatusHistoryRepository.save(BillStatusHistory.builder()
                .bill(savedBill)
                .fromStatus(null)
                .toStatus(BillStatus.PENDING_CONFIRMATION)
                .operator(systemUser)
                .operatorName("SYSTEM")
                .reason("定时任务自动生成账单")
                .ruleVersion(savedBill.getRuleVersion())
                .isAuto(true)
                .build());

        List<BillItem> items;
        try {
            items = billSplitService.splitBill(savedBill, meterReading.getId());
            log.info("已完成账单 (ID: {}) 的明细拆分，共 {} 条明细", savedBill.getId(), items.size());
        } catch (Exception e) {
            String errorMsg = String.format("账单 (ID: %d) 明细拆分失败: %s", savedBill.getId(), e.getMessage());
            log.error(errorMsg, e);
            notificationService.notifyAdmin(savedBill, "账单明细拆分失败",
                    errorMsg + "\n\n请检查分摊规则设置。");
            return new GenerationResultForHousehold(GenerationStatus.FAILED, errorMsg);
        }

        try {
            amountValidationService.validateForPaymentTransition(savedBill, systemUser);
            log.info("账单 (ID: {}) 金额校验通过", savedBill.getId());
        } catch (Exception e) {
            String errorMsg = String.format("账单 (ID: %d) 金额校验失败: %s", savedBill.getId(), e.getMessage());
            log.error(errorMsg, e);
            return new GenerationResultForHousehold(GenerationStatus.FAILED, errorMsg);
        }

        try {
            Bill confirmedBill = stateMachineService.autoTransition(
                    savedBill,
                    BillStatus.PENDING_PAYMENT,
                    "定时任务自动生成账单并确认"
            );
            log.info("账单 (ID: {}) 已自动转为待支付状态", confirmedBill.getId());
        } catch (Exception e) {
            String errorMsg = String.format("账单 (ID: %d) 状态转换失败: %s", savedBill.getId(), e.getMessage());
            log.error(errorMsg, e);
            return new GenerationResultForHousehold(GenerationStatus.FAILED, errorMsg);
        }

        try {
            List<BillItem> savedItems = billItemRepository.findByBillId(savedBill.getId());
            notificationService.notifyBillCreated(savedBill, savedItems);
            log.info("已为账单 (ID: {}) 发送 {} 条缴费通知", savedBill.getId(), savedItems.size());
        } catch (Exception e) {
            log.warn("账单 (ID: {}) 通知发送失败: {}", savedBill.getId(), e.getMessage());
        }

        String statusMsg = estimationResult.isEstimated() ? "（含估算数据）" : "";
        return new GenerationResultForHousehold(
                GenerationStatus.SUCCESS,
                String.format("住户 [%s](ID: %d) 账单生成成功%s，账单ID: %d，总金额: %.2f 元，明细: %d 条",
                        household.getName(), household.getId(), statusMsg,
                        savedBill.getId(), totalAmount, items.size())
        );
    }

    private User getSystemUser() {
        return userRepository.findByUsername("admin")
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
    }

    public boolean shouldGenerateBills() {
        return schedulerConfig.isEnabled();
    }

    public boolean hasPausedTasks() {
        return false;
    }

    private enum GenerationStatus {
        SUCCESS,
        SKIPPED,
        FAILED
    }

    private static class GenerationResultForHousehold {
        private final GenerationStatus status;
        private final String message;

        public GenerationResultForHousehold(GenerationStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public GenerationStatus getStatus() { return status; }
        public String getMessage() { return message; }
    }
}
