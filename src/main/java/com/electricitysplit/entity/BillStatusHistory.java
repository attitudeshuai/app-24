package com.electricitysplit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bill_status_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private BillStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private BillStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "rule_version", nullable = false, length = 20)
    private String ruleVersion;

    @Column(name = "is_auto", nullable = false)
    private Boolean isAuto;

    @CreationTimestamp
    @Column(name = "operated_at", updatable = false)
    private LocalDateTime operatedAt;
}
