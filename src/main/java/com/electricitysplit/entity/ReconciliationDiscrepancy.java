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
@Table(name = "reconciliation_discrepancies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private ReconciliationReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_record_id")
    private PaymentRecord paymentRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 30)
    private DiscrepancyType discrepancyType;

    @Column(name = "system_amount", precision = 12, scale = 2)
    private BigDecimal systemAmount;

    @Column(name = "financial_amount", precision = 12, scale = 2)
    private BigDecimal financialAmount;

    @Column(name = "difference_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal differenceAmount;

    @Column(name = "transaction_no", length = 100)
    private String transactionNo;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_resolved", nullable = false)
    private Boolean isResolved;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.isResolved == null) {
            this.isResolved = false;
        }
        if (this.differenceAmount == null) {
            this.differenceAmount = BigDecimal.ZERO;
        }
    }
}
