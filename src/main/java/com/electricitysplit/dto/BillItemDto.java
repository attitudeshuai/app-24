package com.electricitysplit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BillItemDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "账单ID不能为空")
        private Long billId;

        @NotNull(message = "房间ID不能为空")
        private Long roomId;

        @NotNull(message = "基础分摊不能为空")
        private BigDecimal baseShare;

        @NotNull(message = "空调分摊不能为空")
        private BigDecimal acShare;

        @NotNull(message = "公共分摊不能为空")
        private BigDecimal publicShare;

        private Boolean isPaid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private BigDecimal baseShare;
        private BigDecimal acShare;
        private BigDecimal publicShare;
        private Boolean isPaid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long billId;
        private Long roomId;
        private String roomName;
        private BigDecimal baseShare;
        private BigDecimal acShare;
        private BigDecimal publicShare;
        private BigDecimal totalDue;
        private Boolean isPaid;
        private LocalDateTime paidAt;
        private Boolean isPublicArea;
        private String allocationType;
        private String calculationDetails;
        private Boolean hasRoundingAdjustment;
        private BigDecimal roundingAdjustmentAmount;
    }
}
