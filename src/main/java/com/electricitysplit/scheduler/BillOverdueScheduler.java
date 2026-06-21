package com.electricitysplit.scheduler;

import com.electricitysplit.config.BillStateMachineConfig;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillItem;
import com.electricitysplit.entity.BillStatus;
import com.electricitysplit.repository.BillItemRepository;
import com.electricitysplit.repository.BillRepository;
import com.electricitysplit.service.BillStateMachineService;
import com.electricitysplit.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillOverdueScheduler {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final BillStateMachineService stateMachineService;
    private final NotificationService notificationService;
    private final BillStateMachineConfig stateMachineConfig;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void processOverdueBills() {
        log.info("开始执行账单逾期检查定时任务");
        LocalDate today = LocalDate.now();
        int overdueDays = stateMachineConfig.getOverdueDaysAfterPeriodEnd();
        LocalDate cutoffDate = today.minusDays(overdueDays);

        List<Bill> candidates = billRepository.findOverdueCandidates(BillStatus.PENDING_PAYMENT, cutoffDate);
        log.info("找到 {} 个可能逾期的账单", candidates.size());

        int processedCount = 0;
        for (Bill bill : candidates) {
            try {
                LocalDate effectiveDueDate = bill.getDueDate() != null
                        ? bill.getDueDate()
                        : bill.getPeriodEnd().plusDays(overdueDays);

                if (effectiveDueDate.isBefore(today)) {
                    Bill updated = stateMachineService.autoTransition(
                            bill,
                            BillStatus.OVERDUE,
                            String.format("账单于 %s 到期，系统自动转为逾期状态", effectiveDueDate)
                    );

                    List<BillItem> items = billItemRepository.findByBillId(updated.getId());
                    notificationService.notifyBillOverdue(updated, items);
                    processedCount++;
                }
            } catch (Exception e) {
                log.error("处理账单 {} 逾期时出错: {}", bill.getId(), e.getMessage(), e);
            }
        }

        log.info("账单逾期检查定时任务完成，共处理 {} 个逾期账单", processedCount);
    }

    @Scheduled(cron = "0 0 9 * * MON,WED,FRI")
    @Transactional
    public void sendPaymentReminders() {
        log.info("开始执行缴费提醒定时任务");
        LocalDate today = LocalDate.now();
        LocalDate reminderStart = today.plusDays(1);
        LocalDate reminderEnd = today.plusDays(7);

        List<Bill> candidates = billRepository.findOverdueCandidates(BillStatus.PENDING_PAYMENT, reminderEnd);
        log.info("找到 {} 个即将到期的账单待提醒", candidates.size());

        int reminderCount = 0;
        for (Bill bill : candidates) {
            try {
                LocalDate effectiveDueDate = bill.getDueDate() != null
                        ? bill.getDueDate()
                        : bill.getPeriodEnd().plusDays(stateMachineConfig.getOverdueDaysAfterPeriodEnd());

                if (!effectiveDueDate.isBefore(reminderStart) && !effectiveDueDate.isAfter(reminderEnd)) {
                    List<BillItem> items = billItemRepository.findByBillId(bill.getId());
                    notificationService.notifyBillReminder(bill, items);
                    reminderCount++;
                }
            } catch (Exception e) {
                log.error("发送账单 {} 缴费提醒时出错: {}", bill.getId(), e.getMessage(), e);
            }
        }

        log.info("缴费提醒定时任务完成，共发送 {} 条提醒", reminderCount);
    }
}
