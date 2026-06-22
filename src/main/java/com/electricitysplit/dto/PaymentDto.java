package com.electricitysplit.dto;

import com.electricitysplit.entity.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePaymentRequest {
        @NotNull(message = "账单ID不能为空")
        private Long billId;

        private Long billItemId;

        @NotNull(message = "支付金额不能为空")
        @DecimalMin(value = "0.01", message = "支付金额必须大于0")
        private BigDecimal paymentAmount;

        @NotNull(message = "支付方式不能为空")
        private PaymentMethod paymentMethod;

        @NotBlank(message = "支付流水号不能为空")
        private String transactionNo;

        private LocalDateTime paymentTime;

        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelPaymentRequest {
        @NotNull(message = "支付记录ID不能为空")
        private Long paymentRecordId;

        private String reason;

        private String transactionNo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRequest {
        @NotNull(message = "支付记录ID不能为空")
        private Long paymentRecordId;

        @DecimalMin(value = "0.01", message = "退款金额必须大于0")
        private BigDecimal refundAmount;

        @NotNull(message = "退款方式不能为空")
        private PaymentMethod refundMethod;

        private String transactionNo;

        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRecordResponse {
        private Long id;
        private Long billId;
        private Long billItemId;
        private BigDecimal paymentAmount;
        private PaymentMethod paymentMethod;
        private String transactionNo;
        private LocalDateTime paymentTime;
        private Long operatorId;
        private String operatorName;
        private PaymentType paymentType;
        private Long relatedPaymentId;
        private PaymentStatus status;
        private String remark;
        private Boolean reconciled;
        private LocalDateTime createdAt;

        public static PaymentRecordResponse from(PaymentRecord record) {
            return PaymentRecordResponse.builder()
                    .id(record.getId())
                    .billId(record.getBill() != null ? record.getBill().getId() : null)
                    .billItemId(record.getBillItem() != null ? record.getBillItem().getId() : null)
                    .paymentAmount(record.getPaymentAmount())
                    .paymentMethod(record.getPaymentMethod())
                    .transactionNo(record.getTransactionNo())
                    .paymentTime(record.getPaymentTime())
                    .operatorId(record.getOperator() != null ? record.getOperator().getId() : null)
                    .operatorName(record.getOperatorName())
                    .paymentType(record.getPaymentType())
                    .relatedPaymentId(record.getRelatedPayment() != null ? record.getRelatedPayment().getId() : null)
                    .status(record.getStatus())
                    .remark(record.getRemark())
                    .reconciled(record.getReconciled())
                    .createdAt(record.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummaryResponse {
        private Long billId;
        private BigDecimal totalDue;
        private BigDecimal totalPaid;
        private BigDecimal totalRefunded;
        private BigDecimal netPaid;
        private BigDecimal remainingAmount;
        private Boolean isFullyPaid;
    }
}
