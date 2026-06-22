package com.electricitysplit.service;

import com.electricitysplit.dto.ReconciliationDto;
import com.electricitysplit.entity.*;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final ReconciliationReportRepository reportRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final HouseholdRepository householdRepository;
    private final NotificationService notificationService;

    @Transactional
    public ReconciliationDto.ReportResponse performMonthlyReconciliation(User operator, ReconciliationDto.ReconcileRequest request) {
        LocalDate reportMonth = request.getReportMonth();
        if (reportMonth == null) {
            reportMonth = YearMonth.now().minusMonths(1).atDay(1);
        } else {
            reportMonth = reportMonth.withDayOfMonth(1);
        }

        String operatorName = operator != null ? operator.getUsername() : "SYSTEM";

        if (reportRepository.existsByReportMonth(reportMonth)) {
            ReconciliationReport existing = reportRepository.findByReportMonth(reportMonth).orElseThrow();
            if (existing.getStatus() == ReconciliationStatus.IN_PROGRESS) {
                throw new BusinessException("该月份对账正在进行中，请稍后再试");
            }
            log.info("该月份已存在对账报告，将重新生成对账报告：{}", reportMonth);
            discrepancyRepository.deleteAll(discrepancyRepository.findByReportIdOrderByCreatedAtDesc(existing.getId()));
            reportRepository.delete(existing);
        }

        LocalDateTime startTime = reportMonth.atStartOfDay();
        LocalDateTime endTime = reportMonth.withDayOfMonth(reportMonth.lengthOfMonth()).atTime(23, 59, 59);

        ReconciliationReport report = ReconciliationReport.builder()
                .reportMonth(reportMonth)
                .status(ReconciliationStatus.IN_PROGRESS)
                .operator(operator)
                .operatorName(operatorName)
                .build();
        report = reportRepository.save(report);

        try {
            List<PaymentRecord> paymentRecords = paymentRecordRepository.findByTimeRangeAndTypeAndStatus(
                    startTime, endTime, PaymentType.PAYMENT, PaymentStatus.SUCCESS);

            List<PaymentRecord> refundRecords = paymentRecordRepository.findByTimeRangeAndTypeAndStatus(
                    startTime, endTime, PaymentType.REFUND, PaymentStatus.SUCCESS);

            List<PaymentRecord> cancelRecords = paymentRecordRepository.findByTimeRangeAndTypeAndStatus(
                    startTime, endTime, PaymentType.CANCEL, PaymentStatus.SUCCESS);

            BigDecimal totalPayments = paymentRecords.stream()
                    .map(PaymentRecord::getPaymentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalRefunds = refundRecords.stream()
                    .map(PaymentRecord::getPaymentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCancels = cancelRecords.stream()
                    .map(PaymentRecord::getPaymentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal systemNetAmount = totalPayments.subtract(totalRefunds).subtract(totalCancels);
            int systemTotalCount = paymentRecords.size() + refundRecords.size() + cancelRecords.size();

            report.setSystemTotalAmount(systemNetAmount);
            report.setSystemPaymentCount(systemTotalCount);

            BigDecimal financialTotalAmount = request.getFinancialTotalAmount();
            Integer financialPaymentCount = request.getFinancialPaymentCount();

            List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();

            if (financialTotalAmount != null) {
                BigDecimal diffAmount = systemNetAmount.subtract(financialTotalAmount);
                report.setFinancialTotalAmount(financialTotalAmount);
                report.setDifferenceAmount(diffAmount);

                if (diffAmount.compareTo(BigDecimal.ZERO) != 0) {
                    ReconciliationDiscrepancy amountDiscrepancy = ReconciliationDiscrepancy.builder()
                            .report(report)
                            .discrepancyType(DiscrepancyType.AMOUNT_MISMATCH)
                            .systemAmount(systemNetAmount)
                            .financialAmount(financialTotalAmount)
                            .differenceAmount(diffAmount)
                            .description("系统应收金额与财务数据不一致")
                            .isResolved(false)
                            .build();
                    discrepancies.add(amountDiscrepancy);
                }
            }

            if (financialPaymentCount != null) {
                report.setFinancialPaymentCount(financialPaymentCount);

                int countDiff = systemTotalCount - financialPaymentCount;
                if (countDiff != 0 && financialTotalAmount == null) {
                    ReconciliationDiscrepancy countDiscrepancy = ReconciliationDiscrepancy.builder()
                            .report(report)
                            .discrepancyType(countDiff > 0 ? DiscrepancyType.EXTRA_PAYMENT : DiscrepancyType.MISSING_PAYMENT)
                            .systemAmount(BigDecimal.valueOf(systemTotalCount))
                            .financialAmount(BigDecimal.valueOf(financialPaymentCount))
                            .differenceAmount(BigDecimal.valueOf(Math.abs(countDiff)))
                            .description(countDiff > 0 ? "系统记录数多于财务记录" : "系统记录数少于财务记录")
                            .isResolved(false)
                            .build();
                    discrepancies.add(countDiscrepancy);
                }
            }

            Set<String> transactionNos = new HashSet<>();
            for (PaymentRecord record : paymentRecords) {
                if (!transactionNos.add(record.getTransactionNo())) {
                    ReconciliationDiscrepancy dupDiscrepancy = ReconciliationDiscrepancy.builder()
                            .report(report)
                            .paymentRecord(record)
                            .discrepancyType(DiscrepancyType.DUPLICATE_RECORD)
                            .systemAmount(record.getPaymentAmount())
                            .differenceAmount(record.getPaymentAmount())
                            .transactionNo(record.getTransactionNo())
                            .description("系统内存在重复的支付流水号")
                            .isResolved(false)
                            .build();
                    discrepancies.add(dupDiscrepancy);
                }
            }

            for (ReconciliationDiscrepancy discrepancy : discrepancies) {
                discrepancyRepository.save(discrepancy);
            }

            report.setDiscrepancyCount(discrepancies.size());

            if (discrepancies.isEmpty()) {
                report.setStatus(ReconciliationStatus.COMPLETED);
                report.setRemark("对账完成，无差异");
            } else {
                report.setStatus(ReconciliationStatus.COMPLETED_WITH_DISCREPANCIES);
                report.setRemark("对账完成，发现 " + discrepancies.size() + " 处差异");
            }

            report = reportRepository.save(report);

            if (!discrepancies.isEmpty()) {
                notifyFinancePersonnel(report, discrepancies);
                report.setNotified(true);
                report = reportRepository.save(report);
            }

            markPaymentsAsReconciled(paymentRecords);

            log.info("月末对账完成：月份 {}, 系统金额 {}, 财务金额 {}, 差异数 {}",
                    reportMonth, systemNetAmount, financialTotalAmount, discrepancies.size());

            return ReconciliationDto.ReportResponse.from(report);

        } catch (Exception e) {
            report.setStatus(ReconciliationStatus.FAILED);
            report.setRemark("对账失败：" + e.getMessage());
            reportRepository.save(report);
            log.error("月末对账失败：{}", e.getMessage(), e);
            throw new BusinessException("对账失败：" + e.getMessage());
        }
    }

    public ReconciliationDto.ReportResponse getReportByMonth(LocalDate reportMonth) {
        LocalDate month = reportMonth.withDayOfMonth(1);
        ReconciliationReport report = reportRepository.findByReportMonth(month)
                .orElseThrow(() -> new BusinessException("该月份对账报告不存在"));
        return ReconciliationDto.ReportResponse.from(report);
    }

    public List<ReconciliationDto.ReportResponse> getAllReports() {
        List<ReconciliationReport> reports = reportRepository.findAll();
        return reports.stream()
                .sorted(Comparator.comparing(ReconciliationReport::getReportMonth).reversed())
                .map(ReconciliationDto.ReportResponse::from)
                .collect(Collectors.toList());
    }

    public List<ReconciliationDto.DiscrepancyResponse> getDiscrepanciesByReportId(Long reportId) {
        List<ReconciliationDiscrepancy> discrepancies = discrepancyRepository.findByReportIdOrderByCreatedAtDesc(reportId);
        return discrepancies.stream()
                .map(ReconciliationDto.DiscrepancyResponse::from)
                .collect(Collectors.toList());
    }

    public List<ReconciliationDto.DiscrepancyResponse> getUnresolvedDiscrepancies(Long reportId) {
        List<ReconciliationDiscrepancy> discrepancies = discrepancyRepository.findByReportIdAndIsResolvedOrderByCreatedAtDesc(reportId, false);
        return discrepancies.stream()
                .map(ReconciliationDto.DiscrepancyResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReconciliationDto.DiscrepancyResponse resolveDiscrepancy(User operator, Long discrepancyId, ReconciliationDto.ResolveDiscrepancyRequest request) {
        ReconciliationDiscrepancy discrepancy = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new BusinessException("差异记录不存在"));

        if (Boolean.TRUE.equals(discrepancy.getIsResolved())) {
            throw new BusinessException("该差异已处理");
        }

        discrepancy.setIsResolved(true);
        discrepancy.setResolutionNote(request.getResolutionNote());
        discrepancy.setResolvedAt(LocalDateTime.now());
        discrepancy.setResolvedBy(operator);

        ReconciliationDiscrepancy saved = discrepancyRepository.save(discrepancy);

        ReconciliationReport report = discrepancy.getReport();
        long remainingCount = discrepancyRepository.countByReportIdAndIsResolved(report.getId(), false);
        if (remainingCount == 0) {
            report.setStatus(ReconciliationStatus.COMPLETED);
            reportRepository.save(report);
        }

        log.info("对账差异已处理：差异ID {}, 操作人 {}", discrepancyId, operator.getUsername());

        return ReconciliationDto.DiscrepancyResponse.from(saved);
    }

    private void notifyFinancePersonnel(ReconciliationReport report, List<ReconciliationDiscrepancy> discrepancies) {
        List<Household> households = householdRepository.findAll();
        Set<User> financeUsers = households.stream()
                .map(Household::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        String title = "【对账提醒】" + report.getReportMonth().getYear() + "年" + report.getReportMonth().getMonthValue() + "月对账发现差异";

        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("月末对账发现 ").append(discrepancies.size()).append(" 处差异，请及时处理。\n\n");
        contentBuilder.append("对账月份：").append(report.getReportMonth().getYear()).append("年").append(report.getReportMonth().getMonthValue()).append("月\n");
        contentBuilder.append("系统金额：").append(report.getSystemTotalAmount()).append(" 元\n");
        if (report.getFinancialTotalAmount() != null) {
            contentBuilder.append("财务金额：").append(report.getFinancialTotalAmount()).append(" 元\n");
            contentBuilder.append("差异金额：").append(report.getDifferenceAmount()).append(" 元\n");
        }
        contentBuilder.append("\n差异明细：\n");

        for (int i = 0; i < Math.min(discrepancies.size(), 5); i++) {
            ReconciliationDiscrepancy d = discrepancies.get(i);
            contentBuilder.append(i + 1).append(". ").append(d.getDiscrepancyType())
                    .append(" - ").append(d.getDescription())
                    .append("（差异：").append(d.getDifferenceAmount()).append("元）\n");
        }

        if (discrepancies.size() > 5) {
            contentBuilder.append("... 还有 ").append(discrepancies.size() - 5).append(" 处差异\n");
        }

        for (User financeUser : financeUsers) {
            notificationService.notifyReconciliationDiscrepancy(financeUser, title, contentBuilder.toString());
        }

        log.info("已通知 {} 位财务人员对账差异", financeUsers.size());
    }

    private void markPaymentsAsReconciled(List<PaymentRecord> payments) {
        for (PaymentRecord payment : payments) {
            payment.setReconciled(true);
        }
        paymentRecordRepository.saveAll(payments);
    }
}
