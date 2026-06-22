package com.electricitysplit.dto;

import com.electricitysplit.entity.MeterReadingCorrectionStatus;
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

public class MeterReadingCorrectionDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "住户ID不能为空")
        private Long householdId;

        private Long originalReadingId;

        @NotNull(message = "校正日期不能为空")
        private LocalDate readingDate;

        @NotNull(message = "原总用电量不能为空")
        private BigDecimal originalTotalKwh;

        @NotNull(message = "新总用电量不能为空")
        private BigDecimal newTotalKwh;

        private BigDecimal originalAmount;

        private BigDecimal newAmount;

        @NotBlank(message = "校正原因分类不能为空")
        @Size(max = 50, message = "校正原因分类不能超过50个字符")
        private String correctionReason;

        @NotBlank(message = "校正详细说明不能为空")
        @Size(max = 1000, message = "校正详细说明不能超过1000个字符")
        private String correctionDescription;

        @NotNull(message = "是否换表不能为空")
        private Boolean isMeterChanged;

        private String oldMeterNumber;

        private String newMeterNumber;

        private String newMeterModel;

        private LocalDate newMeterInstallDate;

        private BigDecimal newMeterInitialReading;

        private List<String> proofDocumentUrls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalRequest {
        @NotNull(message = "审批结果不能为空")
        private Boolean approved;

        @Size(max = 500, message = "审批备注不能超过500个字符")
        private String approvalRemark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadProofRequest {
        @NotNull(message = "凭证URL列表不能为空")
        private List<String> proofDocumentUrls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long householdId;
        private String householdName;
        private Long originalReadingId;
        private Long newReadingId;
        private BigDecimal originalTotalKwh;
        private BigDecimal newTotalKwh;
        private BigDecimal originalAmount;
        private BigDecimal newAmount;
        private LocalDate readingDate;
        private String correctionReason;
        private String correctionDescription;
        private Boolean isMeterChanged;
        private String oldMeterNumber;
        private String newMeterNumber;
        private String newMeterModel;
        private LocalDate newMeterInstallDate;
        private BigDecimal newMeterInitialReading;
        private List<String> proofDocumentUrls;
        private MeterReadingCorrectionStatus status;
        private Long applicantId;
        private String applicantName;
        private Long approverId;
        private String approverName;
        private String approvalRemark;
        private Boolean billsRecalculated;
        private String recalculationNote;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime approvedAt;
        private LocalDateTime executedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecalculationResult {
        private Long correctionId;
        private Integer recalculatedBillCount;
        private List<Long> recalculatedBillIds;
        private String note;
    }
}
