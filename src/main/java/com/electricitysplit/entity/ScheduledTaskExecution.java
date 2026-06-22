package com.electricitysplit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_task_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String taskName;

    @Column(name = "task_period", length = 20)
    private String taskPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "is_paused", nullable = false)
    private Boolean isPaused;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "stack_trace", length = 4000)
    private String stackTrace;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "failure_count")
    private Integer failureCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    @Column(name = "details", length = 4000)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        PAUSED,
        RETRYING
    }
}
