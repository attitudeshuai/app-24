package com.electricitysplit.dto;

import com.electricitysplit.entity.Bill;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

        private Bill.BillStatus status;

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
        private Bill.BillStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        @NotNull(message = "状态不能为空")
        private Bill.BillStatus status;
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
        private BigDecimal totalAmount;
        private Bill.BillStatus status;
        private LocalDateTime createdAt;
        private List<BillItemDto.Response> items;
    }
}
