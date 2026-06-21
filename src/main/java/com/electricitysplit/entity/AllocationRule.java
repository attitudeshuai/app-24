package com.electricitysplit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "allocation_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_allocation_type", nullable = false, length = 30)
    private AllocationType baseAllocationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ac_allocation_type", nullable = false, length = 30)
    private AllocationType acAllocationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_allocation_type", nullable = false, length = 30)
    private AllocationType publicAllocationType;

    @Column(name = "public_ratio", precision = 10, scale = 4)
    private BigDecimal publicRatio;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
