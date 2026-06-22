package com.electricitysplit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "scheduler.monthly-bill-generation")
public class SchedulerConfig {

    private boolean enabled = true;

    private String cron = "0 0 1 1 * ?";

    private int maxRetries = 3;

    private int retryDelayMinutes = 60;

    private boolean estimateMissingReadings = true;

    private boolean skipOnEstimationFailure = true;

    private int estimationMonths = 3;

    public static final String TASK_NAME = "MONTHLY_BILL_GENERATION";
}
