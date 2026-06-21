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
@Table(name = "room_custom_ratios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomCustomRatio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_rule_id", nullable = false)
    private AllocationRule allocationRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "base_ratio", precision = 10, scale = 4)
    private BigDecimal baseRatio;

    @Column(name = "ac_ratio", precision = 10, scale = 4)
    private BigDecimal acRatio;

    @Column(name = "public_ratio", precision = 10, scale = 4)
    private BigDecimal publicRatio;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
