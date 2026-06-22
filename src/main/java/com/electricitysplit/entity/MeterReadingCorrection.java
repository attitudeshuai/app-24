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
@Table(name = "meter_reading_corrections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeterReadingCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_reading_id")
    private MeterReading originalReading;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_reading_id")
    private MeterReading newReading;

    @Column(name = "original_total_kwh", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalTotalKwh;

    @Column(name = "new_total_kwh", nullable = false, precision = 12, scale = 2)
    private BigDecimal newTotalKwh;

    @Column(name = "original_amount", precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "new_amount", precision = 12, scale = 2)
    private BigDecimal newAmount;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "correction_reason", nullable = false, length = 50)
    private String correctionReason;

    @Column(name = "correction_description", nullable = false, length = 1000)
    private String correctionDescription;

    @Column(name = "is_meter_changed", nullable = false)
    private Boolean isMeterChanged;

    @Column(name = "old_meter_number", length = 50)
    private String oldMeterNumber;

    @Column(name = "new_meter_number", length = 50)
    private String newMeterNumber;

    @Column(name = "new_meter_model", length = 100)
    private String newMeterModel;

    @Column(name = "new_meter_install_date")
    private LocalDate newMeterInstallDate;

    @Column(name = "new_meter_initial_reading", precision = 12, scale = 2)
    private BigDecimal newMeterInitialReading;

    @Column(name = "proof_document_urls", length = 2000)
    private String proofDocumentUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeterReadingCorrectionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(name = "applicant_name", nullable = false, length = 50)
    private String applicantName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "approver_name", length = 50)
    private String approverName;

    @Column(name = "approval_remark", length = 500)
    private String approvalRemark;

    @Column(name = "bills_recalculated", nullable = false)
    private Boolean billsRecalculated;

    @Column(name = "recalculation_note", length = 500)
    private String recalculationNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
