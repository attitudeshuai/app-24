package com.electricitysplit.service;

import com.electricitysplit.config.SchedulerConfig;
import com.electricitysplit.entity.Notification;
import com.electricitysplit.entity.ScheduledTaskExecution;
import com.electricitysplit.entity.User;
import com.electricitysplit.repository.ScheduledTaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final ScheduledTaskExecutionRepository taskExecutionRepository;
    private final NotificationService notificationService;
    private final SchedulerConfig schedulerConfig;

    @Transactional
    public ScheduledTaskExecution createTaskExecution(String taskName, String taskPeriod) {
        ScheduledTaskExecution execution = ScheduledTaskExecution.builder()
                .taskName(taskName)
                .taskPeriod(taskPeriod)
                .status(ScheduledTaskExecution.TaskStatus.PENDING)
                .retryCount(0)
                .maxRetries(schedulerConfig.getMaxRetries())
                .isPaused(false)
                .successCount(0)
                .failureCount(0)
                .skippedCount(0)
                .build();

        return taskExecutionRepository.save(execution);
    }

    @Transactional
    public ScheduledTaskExecution startExecution(ScheduledTaskExecution execution) {
        execution.setStatus(ScheduledTaskExecution.TaskStatus.RUNNING);
        execution.setErrorMessage(null);
        execution.setStackTrace(null);
        return taskExecutionRepository.save(execution);
    }

    @Transactional
    public ScheduledTaskExecution completeSuccess(ScheduledTaskExecution execution,
                                                   int successCount,
                                                   int failureCount,
                                                   int skippedCount,
                                                   String details) {
        execution.setStatus(ScheduledTaskExecution.TaskStatus.SUCCESS);
        execution.setSuccessCount(successCount);
        execution.setFailureCount(failureCount);
        execution.setSkippedCount(skippedCount);
        execution.setDetails(details);
        execution.setCompletedAt(LocalDateTime.now());
        execution.setIsPaused(false);
        execution.setNextRetryAt(null);
        return taskExecutionRepository.save(execution);
    }

    @Transactional
    public ScheduledTaskExecution handleFailure(ScheduledTaskExecution execution,
                                                 Exception exception,
                                                 int successCount,
                                                 int failureCount,
                                                 int skippedCount,
                                                 String details,
                                                 User adminUser) {
        execution.setRetryCount(execution.getRetryCount() + 1);
        execution.setSuccessCount(successCount);
        execution.setFailureCount(failureCount);
        execution.setSkippedCount(skippedCount);
        execution.setDetails(details);
        execution.setErrorMessage(exception.getMessage());
        execution.setStackTrace(getStackTrace(exception));

        if (execution.getRetryCount() >= execution.getMaxRetries()) {
            execution.setStatus(ScheduledTaskExecution.TaskStatus.PAUSED);
            execution.setIsPaused(true);
            execution.setCompletedAt(LocalDateTime.now());
            execution.setNextRetryAt(null);

            log.error("任务 {} 已达到最大重试次数 ({})，任务已暂停，请人工排查。",
                    execution.getTaskName(), execution.getMaxRetries());

            sendTaskPausedAlert(execution, adminUser);
        } else {
            execution.setStatus(ScheduledTaskExecution.TaskStatus.RETRYING);
            execution.setNextRetryAt(LocalDateTime.now().plusMinutes(schedulerConfig.getRetryDelayMinutes()));

            log.warn("任务 {} 执行失败，准备第 {} 次重试 (最多 {} 次)，下次重试时间: {}",
                    execution.getTaskName(), execution.getRetryCount(),
                    execution.getMaxRetries(), execution.getNextRetryAt());

            sendRetryAlert(execution, adminUser);
        }

        return taskExecutionRepository.save(execution);
    }

    @Transactional
    public void markTaskRunning(ScheduledTaskExecution execution) {
        execution.setStatus(ScheduledTaskExecution.TaskStatus.RUNNING);
        taskExecutionRepository.save(execution);
    }

    @Transactional
    public ScheduledTaskExecution resumeTask(Long executionId, User adminUser) {
        ScheduledTaskExecution execution = taskExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("任务执行记录不存在"));

        if (!execution.getIsPaused()) {
            throw new IllegalStateException("任务未处于暂停状态");
        }

        execution.setIsPaused(false);
        execution.setStatus(ScheduledTaskExecution.TaskStatus.RETRYING);
        execution.setRetryCount(0);
        execution.setNextRetryAt(LocalDateTime.now());
        execution.setErrorMessage(null);
        execution.setStackTrace(null);

        log.info("任务 {} 已由管理员 {} 恢复执行", execution.getTaskName(),
                adminUser != null ? adminUser.getUsername() : "SYSTEM");

        return taskExecutionRepository.save(execution);
    }

    public boolean hasPausedTask(String taskName) {
        List<ScheduledTaskExecution> paused = taskExecutionRepository.findPausedTasks(
                taskName, PageRequest.of(0, 1));
        return !paused.isEmpty();
    }

    public boolean isTaskReadyForRetry(String taskName, ScheduledTaskExecution execution) {
        if (execution.getIsPaused()) {
            return false;
        }
        if (execution.getStatus() != ScheduledTaskExecution.TaskStatus.RETRYING) {
            return false;
        }
        if (execution.getNextRetryAt() == null) {
            return true;
        }
        return !execution.getNextRetryAt().isAfter(LocalDateTime.now());
    }

    public List<ScheduledTaskExecution> getTasksReadyForRetry(String taskName) {
        return taskExecutionRepository.findTasksReadyForRetry(taskName, LocalDateTime.now());
    }

    public ScheduledTaskExecution getLatestExecution(String taskName, String taskPeriod) {
        List<ScheduledTaskExecution> executions = taskExecutionRepository.findLatestByTaskNameAndPeriod(
                taskName, taskPeriod, PageRequest.of(0, 1));
        return executions.isEmpty() ? null : executions.get(0);
    }

    private void sendTaskPausedAlert(ScheduledTaskExecution execution, User adminUser) {
        if (adminUser == null) {
            log.warn("无法发送任务暂停告警：管理员用户为空");
            return;
        }

        String title = String.format("【严重告警】定时任务%s已暂停", execution.getTaskName());
        StringBuilder content = new StringBuilder();
        content.append("定时任务执行失败，已达到最大重试次数，任务已暂停，请尽快人工排查。\n\n");
        content.append("任务名称：").append(execution.getTaskName()).append("\n");
        content.append("任务周期：").append(execution.getTaskPeriod()).append("\n");
        content.append("重试次数：").append(execution.getRetryCount()).append(" / ").append(execution.getMaxRetries()).append("\n");
        content.append("成功数量：").append(execution.getSuccessCount()).append("\n");
        content.append("失败数量：").append(execution.getFailureCount()).append("\n");
        content.append("跳过数量：").append(execution.getSkippedCount()).append("\n");
        content.append("错误信息：").append(execution.getErrorMessage()).append("\n");
        if (execution.getDetails() != null) {
            content.append("详细信息：").append(execution.getDetails()).append("\n");
        }
        content.append("\n请登录系统查看详细错误日志，并在修复问题后恢复任务执行。");

        notificationService.notifyReconciliationDiscrepancy(adminUser, title, content.toString());
        log.error("已发送任务暂停告警给管理员: {}", adminUser.getUsername());
    }

    private void sendRetryAlert(ScheduledTaskExecution execution, User adminUser) {
        if (adminUser == null) {
            return;
        }

        String title = String.format("【告警】定时任务%s执行失败，准备重试", execution.getTaskName());
        StringBuilder content = new StringBuilder();
        content.append("定时任务执行失败，将在延迟后自动重试。\n\n");
        content.append("任务名称：").append(execution.getTaskName()).append("\n");
        content.append("任务周期：").append(execution.getTaskPeriod()).append("\n");
        content.append("当前重试：").append(execution.getRetryCount()).append(" / ").append(execution.getMaxRetries()).append("\n");
        content.append("成功数量：").append(execution.getSuccessCount()).append("\n");
        content.append("失败数量：").append(execution.getFailureCount()).append("\n");
        content.append("跳过数量：").append(execution.getSkippedCount()).append("\n");
        content.append("错误信息：").append(execution.getErrorMessage()).append("\n");
        content.append("下次重试时间：").append(execution.getNextRetryAt()).append("\n");
        if (execution.getDetails() != null) {
            content.append("详细信息：").append(execution.getDetails()).append("\n");
        }

        notificationService.notifyReconciliationDiscrepancy(adminUser, title, content.toString());
        log.info("已发送任务重试告警给管理员: {}", adminUser.getUsername());
    }

    public void sendTaskStartedAlert(String taskName, String taskPeriod, User adminUser) {
        if (adminUser == null) {
            return;
        }

        String title = String.format("【通知】定时任务%s开始执行", taskName);
        String content = String.format("定时任务 %s (周期: %s) 已开始执行，请关注执行结果。",
                taskName, taskPeriod);

        notificationService.notifyReconciliationDiscrepancy(adminUser, title, content);
    }

    public void sendTaskSuccessAlert(String taskName, String taskPeriod,
                                      int successCount, int failureCount, int skippedCount,
                                      String details, User adminUser) {
        if (adminUser == null) {
            return;
        }

        String title = String.format("【通知】定时任务%s执行完成", taskName);
        StringBuilder content = new StringBuilder();
        content.append("定时任务执行完成。\n\n");
        content.append("任务名称：").append(taskName).append("\n");
        content.append("任务周期：").append(taskPeriod).append("\n");
        content.append("成功数量：").append(successCount).append("\n");
        content.append("失败数量：").append(failureCount).append("\n");
        content.append("跳过数量：").append(skippedCount).append("\n");
        if (details != null) {
            content.append("详细信息：").append(details).append("\n");
        }

        notificationService.notifyReconciliationDiscrepancy(adminUser, title, content.toString());
    }

    private String getStackTrace(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        String stackTrace = sw.toString();
        if (stackTrace.length() > 4000) {
            stackTrace = stackTrace.substring(0, 4000);
        }
        return stackTrace;
    }
}
