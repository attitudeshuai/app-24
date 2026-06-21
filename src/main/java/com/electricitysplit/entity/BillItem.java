package com.electricitysplit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "base_share", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseShare;

    @Column(name = "ac_share", nullable = false, precision = 12, scale = 2)
    private BigDecimal acShare;

    @Column(name = "public_share", nullable = false, precision = 12, scale = 2)
    private BigDecimal publicShare;

    @Column(name = "total_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDue;

    @Column(name = "is_paid")
    private Boolean isPaid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
