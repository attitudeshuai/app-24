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
        Long originalReadingId = correction.getOriginalReading() != null
                ? correction.getOriginalReading().getId() : null;

        List<MeterReading> readingsAsc = meterReadingRepository
                .findByHouseholdIdOrderByReadingDateDesc(householdId);
        Collections.reverse(readingsAsc);

        MeterReading prevReading = null;
        MeterReading nextReading = null;
        for (int i = 0; i < readingsAsc.size(); i++) {
            MeterReading r = readingsAsc.get(i);
            boolean isTarget = (originalReadingId != null && r.getId().equals(originalReadingId))
                    || (originalReadingId == null && r.getReadingDate().equals(correctionDate));
            if (isTarget) {
                if (i > 0) prevReading = readingsAsc.get(i - 1);
                if (i < readingsAsc.size() - 1) nextReading = readingsAsc.get(i + 1);
                break;
            }
            if (originalReadingId == null && r.getReadingDate().isBefore(correctionDate)) {
                prevReading = r;
            }
            if (originalReadingId == null && r.getReadingDate().isAfter(correctionDate) && nextReading == null) {
                nextReading = r;
            }
        }

        BigDecimal unitPrice = BigDecimal.ZERO;
        if (prevReading != null && originalAmount != null
                && originalAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal originalUsage = originalTotalKwh.subtract(prevReading.getTotalKwh());
            if (originalUsage.compareTo(BigDecimal.ZERO) > 0) {
                unitPrice = originalAmount.divide(originalUsage, 6, java.math.RoundingMode.HALF_UP);
            }
        }

        BigDecimal calculatedNewAmount = newAmount;
        if (calculatedNewAmount == null && unitPrice.compareTo(BigDecimal.ZERO) > 0 && prevReading != null) {
            BigDecimal newUsage = newTotalKwh.subtract(prevReading.getTotalKwh());
            calculatedNewAmount = newUsage.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
            correction.setNewAmount(calculatedNewAmount);
        }

        MeterReading originalReading = correction.getOriginalReading();
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
                correction, approver, unitPrice);
        correction.setBillsRecalculated(true);
        correction.setRecalculationNote(recalcResult.getNote());
    }

    @Transactional
    protected MeterReadingCorrectionDto.RecalculationResult recalculateAffectedBills(
            MeterReadingCorrection correction, User operator, BigDecimal unitPrice) {
        Long householdId = correction.getHousehold().getId();

        List<MeterReading> readingsAsc = meterReadingRepository
                .findByHouseholdIdOrderByReadingDateDesc(householdId);
        Collections.reverse(readingsAsc);

        Long correctedReadingId = correction.getNewReading() != null
                ? correction.getNewReading().getId() : null;
        LocalDate correctionDate = correction.getReadingDate();

        List<Bill> allBills = billRepository.findByHouseholdIdOrderByPeriodEndDesc(householdId);
        Collections.reverse(allBills);

        List<Bill> affectedBills = new ArrayList<>();
        for (Bill bill : allBills) {
            MeterReading periodStartReading = findPeriodStartReading(readingsAsc, bill.getPeriodStart());
            MeterReading periodEndReading = findPeriodEndReading(readingsAsc, bill.getPeriodEnd());

            if (periodStartReading == null || periodEndReading == null) {
                continue;
            }

            boolean startIsCorrected = isCorrectedReading(periodStartReading, correctedReadingId, correctionDate);
            boolean endIsCorrected = isCorrectedReading(periodEndReading, correctedReadingId, correctionDate);

            if (startIsCorrected || endIsCorrected) {
                affectedBills.add(bill);
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
                MeterReading periodStartReading = findPeriodStartReading(readingsAsc, bill.getPeriodStart());
                MeterReading periodEndReading = findPeriodEndReading(readingsAsc, bill.getPeriodEnd());

                if (periodStartReading != null && periodEndReading != null
                        && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal periodUsage = periodEndReading.getTotalKwh().subtract(periodStartReading.getTotalKwh());
                    if (periodUsage.compareTo(BigDecimal.ZERO) >= 0) {
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
                    ? "已按各周期期初/期末读数与单价重新计算账单金额。"
                    : "";
            noteBuilder.insert(0, String.format("成功重新计算%d个账单(ID列表：%s)。%s",
                    successCount, recalculatedIds, amountUpdateNote));
        } else if (affectedBills.isEmpty()) {
            noteBuilder.append("校正读数未关联任何账单的期初或期末，无需重新计算。");
        }

        return MeterReadingCorrectionDto.RecalculationResult.builder()
                .correctionId(correction.getId())
                .recalculatedBillCount(successCount)
                .recalculatedBillIds(recalculatedIds)
                .note(noteBuilder.toString())
                .build();
    }

    private MeterReading findPeriodStartReading(List<MeterReading> readingsAsc, LocalDate periodStart) {
        MeterReading result = null;
        for (MeterReading r : readingsAsc) {
            if (!r.getReadingDate().isAfter(periodStart)) {
                result = r;
            } else {
                break;
            }
        }
        return result;
    }

    private MeterReading findPeriodEndReading(List<MeterReading> readingsAsc, LocalDate periodEnd) {
        for (MeterReading r : readingsAsc) {
            if (r.getReadingDate().isAfter(periodEnd)) {
                return r;
            }
        }
        return null;
    }

    private boolean isCorrectedReading(MeterReading reading, Long correctedReadingId, LocalDate correctionDate) {
        if (correctedReadingId != null && reading.getId().equals(correctedReadingId)) {
            return true;
        }
        return reading.getReadingDate().equals(correctionDate);
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
