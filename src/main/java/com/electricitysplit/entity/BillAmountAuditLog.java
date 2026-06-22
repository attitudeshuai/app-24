package com.electricitysplit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_amount_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillAmountAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(name = "before_adjustment_details", length = 4000)
    private String beforeAdjustmentDetails;

    @Column(name = "after_adjustment_details", length = 4000)
    private String afterAdjustmentDetails;

    @Column(name = "bill_total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal billTotalAmount;

    @Column(name = "before_sum", nullable = false, precision = 12, scale = 2)
    private BigDecimal beforeSum;

    @Column(name = "after_sum", nullable = false, precision = 12, scale = 2)
    private BigDecimal afterSum;

    @Column(name = "difference", nullable = false, precision = 12, scale = 2)
    private BigDecimal difference;

    @Column(name = "has_rounding_adjustment", nullable = false)
    private Boolean hasRoundingAdjustment;

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
