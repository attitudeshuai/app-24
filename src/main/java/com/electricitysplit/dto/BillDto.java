package com.electricitysplit.dto;

import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillStatus;
import com.electricitysplit.entity.BillStatusHistory;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public class BillDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "住户ID不能为空")
        private Long householdId;

        @NotNull(message = "账期开始日期不能为空")
        private LocalDate periodStart;

        @NotNull(message = "账期结束日期不能为空")
        private LocalDate periodEnd;

        @NotNull(message = "总金额不能为空")
        private BigDecimal totalAmount;

        private LocalDate dueDate;

        private Long meterReadingId;

        private Boolean autoSplit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal totalAmount;
        private LocalDate dueDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransitionRequest {
        @NotNull(message = "目标状态不能为空")
        private BillStatus targetStatus;

        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long householdId;
        private String householdName;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private LocalDate dueDate;
        private BigDecimal totalAmount;
        private BillStatus status;
        private String ruleVersion;
        private Set<BillStatus> allowedTransitions;
        private LocalDateTime createdAt;
        private List<BillItemDto.Response> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryResponse {
        private Long id;
        private Long billId;
        private BillStatus fromStatus;
        private BillStatus toStatus;
        private Long operatorId;
        private String operatorName;
        private String reason;
        private String ruleVersion;
        private Boolean isAuto;
        private LocalDateTime operatedAt;

        public static StatusHistoryResponse from(BillStatusHistory history) {
            return StatusHistoryResponse.builder()
                    .id(history.getId())
                    .billId(history.getBill().getId())
                    .fromStatus(history.getFromStatus())
                    .toStatus(history.getToStatus())
                    .operatorId(history.getOperator() != null ? history.getOperator().getId() : null)
                    .operatorName(history.getOperatorName())
                    .reason(history.getReason())
                    .ruleVersion(history.getRuleVersion())
                    .isAuto(history.getIsAuto())
                    .operatedAt(history.getOperatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbnormalBillResponse {
        private Long billId;
        private BillStatus currentStatus;
        private String ruleVersion;
        private Long householdId;
        private String householdName;
        private LocalDate periodEnd;
        private BigDecimal totalAmount;
        private List<StatusHistoryResponse> suspiciousTransitions;
    }
}
