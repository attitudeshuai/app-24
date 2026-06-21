package com.electricitysplit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MeterReadingDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "住户ID不能为空")
        private Long householdId;

        @NotNull(message = "读数日期不能为空")
        private LocalDate readingDate;

        @NotNull(message = "总用电量不能为空")
        private BigDecimal totalKwh;

        @NotNull(message = "金额不能为空")
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private LocalDate readingDate;
        private BigDecimal totalKwh;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long householdId;
        private String householdName;
        private LocalDate readingDate;
        private BigDecimal totalKwh;
        private BigDecimal amount;
        private LocalDateTime createdAt;
    }
}
