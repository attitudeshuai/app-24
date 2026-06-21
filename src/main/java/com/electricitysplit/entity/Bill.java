package com.electricitysplit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter(AccessLevel.NONE)
    private BillStatus status;

    @Column(name = "rule_version", length = 20)
    @Setter(AccessLevel.NONE)
    private String ruleVersion;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Deprecated
    public void setStatus(BillStatus status) {
        throw new UnsupportedOperationException("禁止直接修改状态字段，请通过 BillStateMachineService.transition() 进行状态转换");
    }

    @Deprecated
    public void setRuleVersion(String ruleVersion) {
        throw new UnsupportedOperationException("禁止直接修改规则版本字段");
    }

    public void setStatusInternal(BillStatus status) {
        this.status = status;
    }

    public void setRuleVersionInternal(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = BillStatus.PENDING_CONFIRMATION;
        }
        if (this.ruleVersion == null) {
            this.ruleVersion = "v1.0";
        }
        if (this.dueDate == null && this.periodEnd != null) {
            this.dueDate = this.periodEnd.plusDays(15);
        }
    }
}
