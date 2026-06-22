package com.electricitysplit.service;

import com.electricitysplit.dto.MeterReadingCorrectionDto;
import com.electricitysplit.entity.*;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeterReadingCorrectionService {

    private final MeterReadingCorrectionRepository correctionRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final HouseholdService householdService;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final BillSplitService billSplitService;
    private final BillStateMachineService billStateMachineService;
    private final BillStatusHistoryRepository billStatusHistoryRepository;

    @Transactional
    public MeterReadingCorrectionDto.Response submitCorrection(User user, MeterReadingCorrectionDto.CreateRequest request) {
        Household household = householdService.getEntityByIdAndCheckPermission(user, request.getHouseholdId());

        MeterReading originalReading = null;
        BigDecimal originalTotalKwh = request.getOriginalTotalKwh();
        BigDecimal originalAmount = request.getOriginalAmount();

        if (request.getOriginalReadingId() != null) {
            originalReading = meterReadingRepository.findById(request.getOriginalReadingId())
                    .orElseThrow(() -> new BusinessException("原始电表读数记录不存在"));
            if (!originalReading.getHousehold().getId().equals(household.getId())) {
                throw new BusinessException("原始读数不属于该住户");
            }
            originalTotalKwh = originalReading.getTotalKwh();
            originalAmount = originalReading.getAmount();
        }

        if (request.getNewTotalKwh().compareTo(originalTotalKwh) >= 0) {
            throw new BusinessException("校正后的读数(" + request.getNewTotalKwh()
                    + ")必须小于原读数(" + originalTotalKwh + ")，正向调整无需走校正流程");
        }

        if (Boolean.TRUE.equals(request.getIsMeterChanged())) {
            if (request.getNewMeterNumber() == null || request.getNewMeterNumber().isBlank()) {
                throw new BusinessException("换表原因必须填写新表编号");
            }
            if (request.getNewMeterInstallDate() == null) {
                throw new BusinessException("换表原因必须填写新表安装日期");
            }
        }

        if (request.getProofDocumentUrls() == null || request.getProofDocumentUrls().isEmpty()) {
            throw new BusinessException("请上传相关凭证作为审批依据");
        }

        MeterReadingCorrection correction = MeterReadingCorrection.builder()
                .household(household)
                .originalReading(originalReading)
                .originalTotalKwh(originalTotalKwh)
                .newTotalKwh(request.getNewTotalKwh())
                .originalAmount(originalAmount)
                .newAmount(request.getNewAmount())
                .readingDate(request.getReadingDate())
                .correctionReason(request.getCorrectionReason())
                .correctionDescription(request.getCorrectionDescription())
                .isMeterChanged(request.getIsMeterChanged())
                .oldMeterNumber(request.getOldMeterNumber())
                .newMeterNumber(request.getNewMeterNumber())
                .newMeterModel(request.getNewMeterModel())
                .newMeterInstallDate(request.getNewMeterInstallDate())
                .newMeterInitialReading(request.getNewMeterInitialReading())
                .proofDocumentUrls(String.join("|", request.getProofDocumentUrls()))
                .status(MeterReadingCorrectionStatus.PENDING_APPROVAL)
                .applicant(user)
                .applicantName(user.getUsername())
                .billsRecalculated(false)
                .build();

        MeterReadingCorrection saved = correctionRepository.save(correction);
        return toResponse(saved);
    }

    public MeterReadingCorrectionDto.Response getById(User user, Long id) {
        MeterReadingCorrection correction = correctionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("校正记录不存在或无权限访问"));
        return toResponse(correction);
    }

    public MeterReadingCorrection getEntityByIdAndCheckPermission(User user, Long id) {
        return correctionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("校正记录不存在或无权限访问"));
    }

    public Page<MeterReadingCorrectionDto.Response> listByHousehold(User user, Long householdId, Pageable pageable) {
        if (householdId == null) {
            throw new BusinessException("必须指定住户ID");
        }
        householdService.getEntityByIdAndCheckPermission(user, householdId);
        Page<MeterReadingCorrection> page = correctionRepository.findByHouseholdId(householdId, pageable);
        return page.map(this::toResponse);
    }

    public Page<MeterReadingCorrectionDto.Response> listMyCorrections(User user, Pageable pageable) {
        Page<MeterReadingCorrection> page = correctionRepository.findByUserId(user.getId(), pageable);
        return page.map(this::toResponse);
    }

    public Page<MeterReadingCorrectionDto.Response> listPendingApprovals(User user, Pageable pageable) {
        checkAdminPermission(user);
        Page<MeterReadingCorrection> page = correctionRepository.findByStatus(
                MeterReadingCorrectionStatus.PENDING_APPROVAL, pageable);
        return page.map(this::toResponse);
    }

    private void checkAdminPermission(User user) {
        if (user == null || user.getRole() != Role.ROLE_ADMIN) {
            throw new BusinessException("无权限执行此操作，需要管理员角色");
        }
    }

    @Transactional
    public MeterReadingCorrectionDto.Response approveCorrection(User approver, Long correctionId,
                                                                  MeterReadingCorrectionDto.ApprovalRequest request) {
        checkAdminPermission(approver);

        MeterReadingCorrection correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> new BusinessException("校正记录不存在"));

        if (correction.getStatus() != MeterReadingCorrectionStatus.PENDING_APPROVAL) {
            throw new BusinessException("仅待审批状态的校正记录可以审批，当前状态：" + correction.getStatus());
        }

        if (correction.getApplicant().getId().equals(approver.getId())) {
            throw new BusinessException("申请人不能审批自己提交的校正申请");
        }

        correction.setApprover(approver);
        correction.setApproverName(approver.getUsername());
        correction.setApprovalRemark(request.getApprovalRemark());
        correction.setApprovedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(request.getApproved())) {
            correction.setStatus(MeterReadingCorrectionStatus.APPROVED);
            executeCorrection(correction, approver);
        } else {
            correction.setStatus(MeterReadingCorrectionStatus.REJECTED);
        }

        MeterReadingCorrection saved = correctionRepository.save(correction);
        return toResponse(saved);
    }

    @Transactional
    public MeterReadingCorrectionDto.Response cancelCorrection(User user, Long correctionId) {
        MeterReadingCorrection correction = getEntityByIdAndCheckPermission(user, correctionId);

        if (correction.getStatus() != MeterReadingCorrectionStatus.PENDING_APPROVAL) {
            throw new BusinessException("仅待审批状态的校正记录可以取消");
        }

        if (!correction.getApplicant().getId().equals(user.getId())) {
            throw new BusinessException("仅申请人可以取消校正申请");
        }

        correction.setStatus(MeterReadingCorrectionStatus.CANCELLED);
        MeterReadingCorrection saved = correctionRepository.save(correction);
        return toResponse(saved);
    }

    @Transactional
    protected void executeCorrection(MeterReadingCorrection correction, User approver) {
        Long householdId = correction.getHousehold().getId();
        LocalDate correctionDate = correction.getReadingDate();
        BigDecimal originalTotalKwh = correction.getOriginalTotalKwh();
        BigDecimal newTotalKwh = correction.getNewTotalKwh();
        BigDecimal originalAmount = correction.getOriginalAmount();
        BigDecimal newAmount = correction.getNewAmount();

        Optional<MeterReading> prevReadingOpt = meterReadingRepository
                .findLatestByHouseholdIdAndDateBefore(householdId, correctionDate, Long.MIN_VALUE);
        Optional<MeterReading> nextReadingOpt = meterReadingRepository
                .findEarliestByHouseholdIdAndDateAfter(householdId, correctionDate);

        BigDecimal unitPrice = BigDecimal.ZERO;
        if (prevReadingOpt.isPresent() && originalAmount != null
                && originalAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal originalUsage = originalTotalKwh.subtract(prevReadingOpt.get().getTotalKwh());
            if (originalUsage.compareTo(BigDecimal.ZERO) > 0) {
                unitPrice = originalAmount.divide(originalUsage, 6, java.math.RoundingMode.HALF_UP);
            }
        }

        MeterReading originalReading = correction.getOriginalReading();
        BigDecimal calculatedNewAmount = newAmount;
        if (calculatedNewAmount == null && unitPrice.compareTo(BigDecimal.ZERO) > 0
                && prevReadingOpt.isPresent()) {
            BigDecimal newUsage = newTotalKwh.subtract(prevReadingOpt.get().getTotalKwh());
            calculatedNewAmount = newUsage.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
            correction.setNewAmount(calculatedNewAmount);
        }

        if (originalReading != null) {
            originalReading.setTotalKwh(newTotalKwh);
            if (calculatedNewAmount != null) {
                originalReading.setAmount(calculatedNewAmount);
            }
            originalReading.setReadingDate(correctionDate);
            MeterReading updated = meterReadingRepository.save(originalReading);
            correction.setNewReading(updated);
        } else {
            MeterReading newReading = MeterReading.builder()
                    .household(correction.getHousehold())
                    .readingDate(correctionDate)
                    .totalKwh(newTotalKwh)
                    .amount(calculatedNewAmount != null ? calculatedNewAmount : BigDecimal.ZERO)
                    .build();
            MeterReading saved = meterReadingRepository.save(newReading);
            correction.setNewReading(saved);
        }

        correction.setExecutedAt(LocalDateTime.now());

        MeterReadingCorrectionDto.RecalculationResult recalcResult = recalculateAffectedBills(
                correction, approver, unitPrice, prevReadingOpt.orElse(null), nextReadingOpt.orElse(null));
        correction.setBillsRecalculated(true);
        correction.setRecalculationNote(recalcResult.getNote());
    }

    @Transactional
    protected MeterReadingCorrectionDto.RecalculationResult recalculateAffectedBills(
            MeterReadingCorrection correction, User operator, BigDecimal unitPrice,
            MeterReading prevReading, MeterReading nextReading) {
        Long householdId = correction.getHousehold().getId();
        LocalDate correctionDate = correction.getReadingDate();
        BigDecimal newTotalKwh = correction.getNewTotalKwh();

        List<Bill> allBills = billRepository.findByHouseholdIdOrderByPeriodEndDesc(householdId);
        Collections.reverse(allBills);

        List<Bill> affectedBills = new ArrayList<>();
        for (Bill bill : allBills) {
            LocalDate periodStart = bill.getPeriodStart();
            LocalDate periodEnd = bill.getPeriodEnd();

            boolean isCurrentPeriodBill = prevReading != null
                    && !periodEnd.isBefore(prevReading.getReadingDate())
                    && periodEnd.isBefore(nextReading != null ? nextReading.getReadingDate() : correctionDate.plusYears(1));

            boolean isNextPeriodBill = nextReading != null
                    && !periodEnd.isBefore(correctionDate)
                    && periodEnd.isBefore(nextReading.getReadingDate().plusDays(1));

            if (isCurrentPeriodBill || isNextPeriodBill) {
                affectedBills.add(bill);
            }
        }

        if (affectedBills.isEmpty() && !allBills.isEmpty()) {
            for (Bill bill : allBills) {
                if (!bill.getPeriodEnd().isBefore(correctionDate.minusMonths(1))) {
                    affectedBills.add(bill);
                    break;
                }
            }
            if (nextReading != null) {
                for (Bill bill : allBills) {
                    if (!bill.getPeriodStart().isBefore(correctionDate)
                            && !affectedBills.contains(bill)) {
                        affectedBills.add(bill);
                        break;
                    }
                }
            }
        }

        List<Long> recalculatedIds = new ArrayList<>();
        int successCount = 0;
        StringBuilder noteBuilder = new StringBuilder();

        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            noteBuilder.append("无法计算单价，跳过账单金额更新，仅重新分摊明细。");
        }

        for (Bill bill : affectedBills) {
            try {
                if (unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal periodUsage = calculatePeriodUsage(bill, correction, prevReading, nextReading);
                    if (periodUsage != null && periodUsage.compareTo(BigDecimal.ZERO) >= 0) {
                        BigDecimal periodAmount = periodUsage.multiply(unitPrice)
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                        bill.setTotalAmount(periodAmount);
                        billRepository.save(bill);
                    }
                }

                List<BillItem> oldItems = billItemRepository.findByBillId(bill.getId());
                billItemRepository.deleteAll(oldItems);

                Long readingId = correction.getNewReading() != null ? correction.getNewReading().getId() : null;
                billSplitService.splitBill(bill, readingId);

                billStatusHistoryRepository.save(BillStatusHistory.builder()
                        .bill(bill)
                        .fromStatus(bill.getStatus())
                        .toStatus(bill.getStatus())
                        .operator(operator)
                        .operatorName(operator.getUsername())
                        .reason("电表读数校正(ID=" + correction.getId() + ")触发账单总金额及明细重新计算")
                        .ruleVersion(bill.getRuleVersion())
                        .isAuto(false)
                        .build());

                recalculatedIds.add(bill.getId());
                successCount++;
            } catch (Exception e) {
                noteBuilder.append(String.format("账单ID=%d重新计算失败：%s；", bill.getId(), e.getMessage()));
            }
        }

        if (successCount > 0) {
            String amountUpdateNote = unitPrice.compareTo(BigDecimal.ZERO) > 0
                    ? "已按各周期用电量和单价重新计算账单金额。"
                    : "";
            noteBuilder.insert(0, String.format("成功重新计算%d个账单(ID列表：%s)。%s",
                    successCount, recalculatedIds, amountUpdateNote));
        } else if (affectedBills.isEmpty()) {
            noteBuilder.append("校正日期附近无关联账单，无需重新计算。");
        }

        return MeterReadingCorrectionDto.RecalculationResult.builder()
                .correctionId(correction.getId())
                .recalculatedBillCount(successCount)
                .recalculatedBillIds(recalculatedIds)
                .note(noteBuilder.toString())
                .build();
    }

    private BigDecimal calculatePeriodUsage(Bill bill, MeterReadingCorrection correction,
                                            MeterReading prevReading, MeterReading nextReading) {
        LocalDate periodStart = bill.getPeriodStart();
        LocalDate periodEnd = bill.getPeriodEnd();
        BigDecimal newTotalKwh = correction.getNewTotalKwh();
        LocalDate correctionDate = correction.getReadingDate();

        BigDecimal startReading = null;
        BigDecimal endReading = null;

        if (prevReading != null && !periodStart.isBefore(prevReading.getReadingDate())) {
            startReading = prevReading.getTotalKwh();
        }

        if (nextReading != null && !periodEnd.isAfter(nextReading.getReadingDate())) {
            endReading = nextReading.getTotalKwh();
        }

        if (!correctionDate.isBefore(periodStart) && !correctionDate.isAfter(periodEnd.plusDays(1))) {
            if (startReading == null && prevReading != null) {
                startReading = prevReading.getTotalKwh();
            }
            if (endReading == null && nextReading != null) {
                endReading = nextReading.getTotalKwh();
            }
            if (endReading == null) {
                endReading = newTotalKwh;
            }
        }

        if (startReading == null && prevReading != null
                && periodStart.isAfter(prevReading.getReadingDate())
                && periodStart.isBefore(correctionDate.plusDays(1))) {
            startReading = prevReading.getTotalKwh();
        }
        if (endReading == null && nextReading != null
                && periodEnd.isBefore(nextReading.getReadingDate().plusDays(1))
                && periodEnd.isAfter(correctionDate.minusDays(1))) {
            endReading = nextReading.getTotalKwh();
        }

        if (startReading != null && endReading != null) {
            return endReading.subtract(startReading);
        }

        return null;
    }

    @Transactional
    public MeterReadingCorrectionDto.Response uploadProof(User user, Long correctionId,
                                                           MeterReadingCorrectionDto.UploadProofRequest request) {
        MeterReadingCorrection correction = getEntityByIdAndCheckPermission(user, correctionId);

        if (correction.getStatus() != MeterReadingCorrectionStatus.PENDING_APPROVAL) {
            throw new BusinessException("仅待审批状态的校正记录可以补充凭证");
        }

        if (!correction.getApplicant().getId().equals(user.getId())) {
            throw new BusinessException("仅申请人可以补充审批凭证");
        }

        correction.setProofDocumentUrls(String.join("|", request.getProofDocumentUrls()));
        MeterReadingCorrection saved = correctionRepository.save(correction);
        return toResponse(saved);
    }

    private MeterReadingCorrectionDto.Response toResponse(MeterReadingCorrection c) {
        List<String> proofUrls = c.getProofDocumentUrls() != null && !c.getProofDocumentUrls().isBlank()
                ? Arrays.asList(c.getProofDocumentUrls().split("\\|"))
                : Collections.emptyList();

        return MeterReadingCorrectionDto.Response.builder()
                .id(c.getId())
                .householdId(c.getHousehold().getId())
                .householdName(c.getHousehold().getName())
                .originalReadingId(c.getOriginalReading() != null ? c.getOriginalReading().getId() : null)
                .newReadingId(c.getNewReading() != null ? c.getNewReading().getId() : null)
                .originalTotalKwh(c.getOriginalTotalKwh())
                .newTotalKwh(c.getNewTotalKwh())
                .originalAmount(c.getOriginalAmount())
                .newAmount(c.getNewAmount())
                .readingDate(c.getReadingDate())
                .correctionReason(c.getCorrectionReason())
                .correctionDescription(c.getCorrectionDescription())
                .isMeterChanged(c.getIsMeterChanged())
                .oldMeterNumber(c.getOldMeterNumber())
                .newMeterNumber(c.getNewMeterNumber())
                .newMeterModel(c.getNewMeterModel())
                .newMeterInstallDate(c.getNewMeterInstallDate())
                .newMeterInitialReading(c.getNewMeterInitialReading())
                .proofDocumentUrls(proofUrls)
                .status(c.getStatus())
                .applicantId(c.getApplicant().getId())
                .applicantName(c.getApplicantName())
                .approverId(c.getApprover() != null ? c.getApprover().getId() : null)
                .approverName(c.getApproverName())
                .approvalRemark(c.getApprovalRemark())
                .billsRecalculated(c.getBillsRecalculated())
                .recalculationNote(c.getRecalculationNote())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .approvedAt(c.getApprovedAt())
                .executedAt(c.getExecutedAt())
                .build();
    }
}
