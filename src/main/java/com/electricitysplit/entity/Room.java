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
@Table(name = "rooms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(precision = 10, scale = 2)
    private BigDecimal area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupant_id")
    private User occupant;

    @Column(name = "has_air_conditioner")
    private Boolean hasAirConditioner;

    @Column(name = "head_count")
    private Integer headCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    @Column(name = "delete_reason", length = 500)
    private String deleteReason;

    @PrePersist
    protected void onCreate() {
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }
}
