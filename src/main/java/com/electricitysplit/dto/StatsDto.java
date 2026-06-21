package com.electricitysplit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class StatsDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewResponse {
        private long totalHouseholds;
        private long totalRooms;
        private long totalBills;
        private long paidBills;
        private long pendingBills;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItem {
        private String period;
        private BigDecimal amount;
        private long billCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendResponse {
        private List<TrendItem> items;
    }
}
