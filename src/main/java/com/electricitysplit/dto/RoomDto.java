package com.electricitysplit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class RoomDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "住户ID不能为空")
        private Long householdId;

        @NotBlank(message = "房间名称不能为空")
        @Size(max = 50, message = "房间名称不能超过50个字符")
        private String name;

        private BigDecimal area;

        private Long occupantId;

        private Boolean hasAirConditioner;

        private Integer headCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 50, message = "房间名称不能超过50个字符")
        private String name;

        private BigDecimal area;

        private Long occupantId;

        private Boolean hasAirConditioner;

        private Integer headCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteRequest {
        @Size(max = 500, message = "删除原因不能超过500个字符")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteCheckResult {
        private boolean canDelete;
        private List<ConflictItem> conflicts;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictItem {
        private String type;
        private String description;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long householdId;
        private String householdName;
        private String name;
        private BigDecimal area;
        private Long occupantId;
        private String occupantUsername;
        private Boolean hasAirConditioner;

        private Integer headCount;

        private LocalDateTime createdAt;

        private Boolean isDeleted;
        private LocalDateTime deletedAt;
        private Long deletedBy;
        private String deletedByUsername;
        private String deleteReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomDataExport {
        private RoomBasicInfo room;
        private List<MeterReadingExport> meterReadings;
        private List<BillExport> bills;
        private List<BillItemExport> billItems;
        private List<PaymentRecordExport> paymentRecords;
        private ExportSummary summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomBasicInfo {
        private Long id;
        private String name;
        private BigDecimal area;
        private Boolean hasAirConditioner;
        private Integer headCount;
        private LocalDateTime createdAt;
        private String householdName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeterReadingExport {
        private Long id;
        private LocalDate readingDate;
        private BigDecimal previousReading;
        private BigDecimal currentReading;
        private BigDecimal usageKwh;
        private Boolean isAcUsage;
        private BigDecimal totalKwh;
        private BigDecimal amount;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillExport {
        private Long id;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal totalAmount;
        private String status;
        private LocalDate dueDate;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillItemExport {
        private Long id;
        private Long billId;
        private BigDecimal baseShare;
        private BigDecimal acShare;
        private BigDecimal publicShare;
        private BigDecimal totalDue;
        private Boolean isPaid;
        private LocalDateTime paidAt;
        private Boolean isPublicArea;
        private String allocationType;
        private String calculationDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRecordExport {
        private Long id;
        private Long billId;
        private Long billItemId;
        private BigDecimal paymentAmount;
        private String paymentMethod;
        private String transactionNo;
        private LocalDateTime paymentTime;
        private String operatorName;
        private String paymentType;
        private String status;
        private String remark;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportSummary {
        private int meterReadingCount;
        private int billCount;
        private int billItemCount;
        private int paymentRecordCount;
        private BigDecimal totalBillAmount;
        private BigDecimal totalPaymentAmount;
        private LocalDateTime exportTime;
    }
}
