package com.electricitysplit.dto;

import com.electricitysplit.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReconciliationDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconcileRequest {
        private LocalDate reportMonth;
        private BigDecimal financialTotalAmount;
        private Integer financialPaymentCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportResponse {
        private Long id;
        private LocalDate reportMonth;
        private BigDecimal systemTotalAmount;
        private Integer systemPaymentCount;
        private BigDecimal financialTotalAmount;
        private Integer financialPaymentCount;
        private BigDecimal differenceAmount;
        private Integer discrepancyCount;
        private ReconciliationStatus status;
        private String remark;
        private Long operatorId;
        private String operatorName;
        private Boolean notified;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static ReportResponse from(ReconciliationReport report) {
            return ReportResponse.builder()
                    .id(report.getId())
                    .reportMonth(report.getReportMonth())
                    .systemTotalAmount(report.getSystemTotalAmount())
                    .systemPaymentCount(report.getSystemPaymentCount())
                    .financialTotalAmount(report.getFinancialTotalAmount())
                    .financialPaymentCount(report.getFinancialPaymentCount())
                    .differenceAmount(report.getDifferenceAmount())
                    .discrepancyCount(report.getDiscrepancyCount())
                    .status(report.getStatus())
                    .remark(report.getRemark())
                    .operatorId(report.getOperator() != null ? report.getOperator().getId() : null)
                    .operatorName(report.getOperatorName())
                    .notified(report.getNotified())
                    .createdAt(report.getCreatedAt())
                    .updatedAt(report.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscrepancyResponse {
        private Long id;
        private Long reportId;
        private Long paymentRecordId;
        private DiscrepancyType discrepancyType;
        private BigDecimal systemAmount;
        private BigDecimal financialAmount;
        private BigDecimal differenceAmount;
        private String transactionNo;
        private String description;
        private Boolean isResolved;
        private String resolutionNote;
        private LocalDateTime resolvedAt;
        private Long resolvedById;
        private LocalDateTime createdAt;

        public static DiscrepancyResponse from(ReconciliationDiscrepancy discrepancy) {
            return DiscrepancyResponse.builder()
                    .id(discrepancy.getId())
                    .reportId(discrepancy.getReport() != null ? discrepancy.getReport().getId() : null)
                    .paymentRecordId(discrepancy.getPaymentRecord() != null ? discrepancy.getPaymentRecord().getId() : null)
                    .discrepancyType(discrepancy.getDiscrepancyType())
                    .systemAmount(discrepancy.getSystemAmount())
                    .financialAmount(discrepancy.getFinancialAmount())
                    .differenceAmount(discrepancy.getDifferenceAmount())
                    .transactionNo(discrepancy.getTransactionNo())
                    .description(discrepancy.getDescription())
                    .isResolved(discrepancy.getIsResolved())
                    .resolutionNote(discrepancy.getResolutionNote())
                    .resolvedAt(discrepancy.getResolvedAt())
                    .resolvedById(discrepancy.getResolvedBy() != null ? discrepancy.getResolvedBy().getId() : null)
                    .createdAt(discrepancy.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolveDiscrepancyRequest {
        private String resolutionNote;
    }
}
