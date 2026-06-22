package com.electricitysplit.scheduler;

import com.electricitysplit.config.SchedulerConfig;
import com.electricitysplit.entity.ScheduledTaskExecution;
import com.electricitysplit.entity.User;
import com.electricitysplit.repository.UserRepository;
import com.electricitysplit.service.MonthlyBillGenerationService;
import com.electricitysplit.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyBillGenerationScheduler {

    private final MonthlyBillGenerationService billGenerationService;
    private final ScheduledTaskService scheduledTaskService;
    private final SchedulerConfig schedulerConfig;
    private final UserRepository userRepository;

    @Scheduled(cron = "${scheduler.monthly-bill-generation.cron:0 0 1 1 * ?}")
    @Transactional
    public void generateMonthlyBills() {
        if (!schedulerConfig.isEnabled()) {
            log.info("月度账单生成定时任务已禁用，跳过执行");
            return;
        }

        YearMonth billingPeriod = YearMonth.now().minusMonths(1);
        String periodStr = billingPeriod.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        if (scheduledTaskService.hasPausedTask(SchedulerConfig.TASK_NAME)) {
            log.warn("存在已暂停的 {} 任务，请先处理后再执行新任务", SchedulerConfig.TASK_NAME);
            return;
        }

        ScheduledTaskExecution existingExecution = scheduledTaskService.getLatestExecution(
                SchedulerConfig.TASK_NAME, periodStr);

        if (existingExecution != null &&
                existingExecution.getStatus() == ScheduledTaskExecution.TaskStatus.SUCCESS) {
            log.info("{} 账期的账单已成功生成，跳过重复执行", periodStr);
            return;
        }

        log.info("========== 开始执行月度账单生成定时任务 ==========");
        log.info("任务名称: {}, 账期: {}", SchedulerConfig.TASK_NAME, periodStr);

        ScheduledTaskExecution execution = existingExecution != null
                ? existingExecution
                : scheduledTaskService.createTaskExecution(SchedulerConfig.TASK_NAME, periodStr);

        executeTask(execution, billingPeriod);
    }

    @Scheduled(fixedRateString = "${scheduler.monthly-bill-generation.retry-check-interval:300000}")
    @Transactional
    public void checkAndRetryFailedTasks() {
        if (!schedulerConfig.isEnabled()) {
            return;
        }

        List<ScheduledTaskExecution> retryTasks = scheduledTaskService.getTasksReadyForRetry(
                SchedulerConfig.TASK_NAME);

        for (ScheduledTaskExecution execution : retryTasks) {
            try {
                YearMonth billingPeriod = YearMonth.parse(execution.getTaskPeriod(),
                        DateTimeFormatter.ofPattern("yyyy-MM"));

                log.info("========== 开始重试月度账单生成任务 ==========");
                log.info("任务执行ID: {}, 账期: {}, 当前重试次数: {}/{}",
                        execution.getId(), execution.getTaskPeriod(),
                        execution.getRetryCount(), execution.getMaxRetries());

                executeTask(execution, billingPeriod);
            } catch (Exception e) {
                log.error("处理重试任务时发生错误，任务执行ID: {}", execution.getId(), e);
            }
        }
    }

    private void executeTask(ScheduledTaskExecution execution, YearMonth billingPeriod) {
        User adminUser = getAdminUser();
        String periodStr = billingPeriod.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        try {
            scheduledTaskService.startExecution(execution);
            scheduledTaskService.sendTaskStartedAlert(
                    SchedulerConfig.TASK_NAME, periodStr, adminUser);

            MonthlyBillGenerationService.GenerationResult result =
                    billGenerationService.generateMonthlyBills(billingPeriod);

            String details = String.join("\n", result.getDetails());

            if (result.getFailureCount() > 0 && !result.getErrors().isEmpty()) {
                details += "\n\n错误详情:\n" + String.join("\n", result.getErrors());
            }

            if (result.getFailureCount() > 0) {
                throw new RuntimeException(String.format(
                        "账单生成过程中发生错误，成功: %d, 失败: %d, 跳过: %d",
                        result.getSuccessCount(), result.getFailureCount(), result.getSkippedCount()));
            }

            scheduledTaskService.completeSuccess(
                    execution,
                    result.getSuccessCount(),
                    result.getFailureCount(),
                    result.getSkippedCount(),
                    details
            );

            scheduledTaskService.sendTaskSuccessAlert(
                    SchedulerConfig.TASK_NAME,
                    periodStr,
                    result.getSuccessCount(),
                    result.getFailureCount(),
                    result.getSkippedCount(),
                    details,
                    adminUser
            );

            log.info("========== 月度账单生成定时任务执行成功 ==========");
            log.info("成功: {}, 失败: {}, 跳过: {}",
                    result.getSuccessCount(), result.getFailureCount(), result.getSkippedCount());

        } catch (Exception e) {
            log.error("月度账单生成定时任务执行失败: {}", e.getMessage(), e);

            int successCount = execution.getSuccessCount() != null ? execution.getSuccessCount() : 0;
            int failureCount = execution.getFailureCount() != null ? execution.getFailureCount() : 0;
            int skippedCount = execution.getSkippedCount() != null ? execution.getSkippedCount() : 0;

            String details = execution.getDetails() != null ? execution.getDetails() : "";

            scheduledTaskService.handleFailure(
                    execution,
                    e,
                    successCount,
                    failureCount,
                    skippedCount,
                    details,
                    adminUser
            );

            log.warn("========== 月度账单生成定时任务执行失败，已记录并准备重试 ==========");
        }
    }

    private User getAdminUser() {
        return userRepository.findByUsername("admin")
                .orElseGet(() -> userRepository.findAll().stream()
                        .findFirst()
                        .orElse(null));
    }
}
