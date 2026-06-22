package com.electricitysplit.scheduler;

import com.electricitysplit.dto.ReconciliationDto;
import com.electricitysplit.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    @Scheduled(cron = "0 0 3 1 * ?")
    @Transactional
    public void performMonthlyReconciliation() {
        log.info("开始执行月末对账定时任务");

        try {
            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            LocalDate reportMonth = lastMonth.atDay(1);

            ReconciliationDto.ReconcileRequest request = ReconciliationDto.ReconcileRequest.builder()
                    .reportMonth(reportMonth)
                    .build();

            ReconciliationDto.ReportResponse response = reconciliationService.performMonthlyReconciliation(null, request);

            log.info("月末对账定时任务完成：月份 {}, 状态 {}, 差异数 {}",
                    reportMonth, response.getStatus(), response.getDiscrepancyCount());

        } catch (Exception e) {
            log.error("月末对账定时任务执行失败：{}", e.getMessage(), e);
        }
    }
}
