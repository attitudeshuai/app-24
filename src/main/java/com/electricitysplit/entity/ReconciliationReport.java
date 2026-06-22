package com.electricitysplit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_month", nullable = false)
    private LocalDate reportMonth;

    @Column(name = "system_total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal systemTotalAmount;

    @Column(name = "system_payment_count", nullable = false)
    private Integer systemPaymentCount;

    @Column(name = "financial_total_amount", precision = 12, scale = 2)
    private BigDecimal financialTotalAmount;

    @Column(name = "financial_payment_count")
    private Integer financialPaymentCount;

    @Column(name = "difference_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal differenceAmount;

    @Column(name = "discrepancy_count", nullable = false)
    private Integer discrepancyCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReconciliationStatus status;

    @Column(name = "remark", length = 500)
    private String remark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "notified", nullable = false)
    private Boolean notified;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = ReconciliationStatus.PENDING;
        }
        if (this.notified == null) {
            this.notified = false;
        }
        if (this.differenceAmount == null) {
            this.differenceAmount = BigDecimal.ZERO;
        }
        if (this.discrepancyCount == null) {
            this.discrepancyCount = 0;
        }
    }
}
