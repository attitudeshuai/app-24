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
        Page<MeterReadingCorrection> page = correctionRepository.findByStatus(
                MeterReadingCorrectionStatus.PENDING_APPROVAL, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public MeterReadingCorrectionDto.Response approveCorrection(User approver, Long correctionId,
                                                                  MeterReadingCorrectionDto.ApprovalRequest request) {
        MeterReadingCorrection correction = getEntityByIdAndCheckPermission(approver, correctionId);

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
        MeterReading originalReading = correction.getOriginalReading();
        if (originalReading != null) {
            originalReading.setTotalKwh(correction.getNewTotalKwh());
            if (correction.getNewAmount() != null) {
                originalReading.setAmount(correction.getNewAmount());
            }
            originalReading.setReadingDate(correction.getReadingDate());
            MeterReading updated = meterReadingRepository.save(originalReading);
            correction.setNewReading(updated);
        } else {
            MeterReading newReading = MeterReading.builder()
                    .household(correction.getHousehold())
                    .readingDate(correction.getReadingDate())
                    .totalKwh(correction.getNewTotalKwh())
                    .amount(correction.getNewAmount() != null ? correction.getNewAmount() : BigDecimal.ZERO)
                    .build();
            MeterReading saved = meterReadingRepository.save(newReading);
            correction.setNewReading(saved);
        }

        correction.setExecutedAt(LocalDateTime.now());

        MeterReadingCorrectionDto.RecalculationResult recalcResult = recalculateAffectedBills(correction, approver);
        correction.setBillsRecalculated(true);
        correction.setRecalculationNote(recalcResult.getNote());
    }

    @Transactional
    protected MeterReadingCorrectionDto.RecalculationResult recalculateAffectedBills(
            MeterReadingCorrection correction, User operator) {
        LocalDate correctionDate = correction.getReadingDate();
        Long householdId = correction.getHousehold().getId();

        List<Bill> affectedBills = billRepository.findByHouseholdIdOrderByPeriodEndDesc(householdId)
                .stream()
                .filter(bill -> !bill.getPeriodEnd().isBefore(correctionDate.minusDays(1)))
                .collect(Collectors.toList());

        Collections.reverse(affectedBills);

        List<Long> recalculatedIds = new ArrayList<>();
        int successCount = 0;
        StringBuilder noteBuilder = new StringBuilder();

        for (Bill bill : affectedBills) {
            try {
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
                        .reason("电表读数校正(ID=" + correction.getId() + ")触发账单明细重新计算")
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
            noteBuilder.insert(0, String.format("成功重新计算%d个账单(ID列表：%s)。",
                    successCount, recalculatedIds));
        } else if (affectedBills.isEmpty()) {
            noteBuilder.append("校正日期之后无关联账单，无需重新计算。");
        }

        return MeterReadingCorrectionDto.RecalculationResult.builder()
                .correctionId(correction.getId())
                .recalculatedBillCount(successCount)
                .recalculatedBillIds(recalculatedIds)
                .note(noteBuilder.toString())
                .build();
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
