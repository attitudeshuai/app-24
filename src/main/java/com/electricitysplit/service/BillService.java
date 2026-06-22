package com.electricitysplit.service;

import com.electricitysplit.config.BillStateMachineConfig;
import com.electricitysplit.dto.BillDto;
import com.electricitysplit.dto.BillItemDto;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillItem;
import com.electricitysplit.entity.BillStatus;
import com.electricitysplit.entity.BillStatusHistory;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.BillItemRepository;
import com.electricitysplit.repository.BillRepository;
import com.electricitysplit.repository.BillStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final BillStatusHistoryRepository billStatusHistoryRepository;
    private final HouseholdService householdService;
    private final BillSplitService billSplitService;
    private final NotificationService notificationService;
    private final BillStateMachineService stateMachineService;
    private final BillStateMachineConfig stateMachineConfig;
    private final BillAmountValidationService amountValidationService;
    private final com.electricitysplit.repository.BillAmountAuditLogRepository billAmountAuditLogRepository;

    @Transactional
    public BillDto.Response create(User user, BillDto.CreateRequest request) {
        Household household = householdService.getEntityByIdAndCheckPermission(user, request.getHouseholdId());

        Bill bill = Bill.builder()
                .household(household)
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .totalAmount(request.getTotalAmount())
                .dueDate(request.getDueDate())
                .build();

        Bill saved = billRepository.save(bill);

        billStatusHistoryRepository.save(BillStatusHistory.builder()
                .bill(saved)
                .fromStatus(null)
                .toStatus(saved.getStatus())
                .operator(user)
                .operatorName(user.getUsername())
                .reason("创建账单")
                .ruleVersion(saved.getRuleVersion())
                .isAuto(false)
                .build());

        if (Boolean.TRUE.equals(request.getAutoSplit())) {
            var items = billSplitService.splitBill(saved, request.getMeterReadingId());
            amountValidationService.validateForPaymentTransition(saved);
            Bill confirmed = stateMachineService.transition(
                    user, saved, BillStatus.PENDING_PAYMENT, "创建账单后自动确认并发送");
            notificationService.notifyBillCreated(confirmed, items);
            return toResponse(confirmed);
        }

        return toResponse(saved);
    }

    public BillDto.Response getById(User user, Long id) {
        Bill bill = billRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("账单不存在或无权限访问"));
        return toResponse(bill);
    }

    public Bill getEntityByIdAndCheckPermission(User user, Long id) {
        return billRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("账单不存在或无权限访问"));
    }

    public Page<BillDto.Response> list(User user, Long householdId, BillStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (householdId == null) {
            throw new BusinessException("必须指定住户ID");
        }
        householdService.getEntityByIdAndCheckPermission(user, householdId);

        Page<Bill> page;

        if (status != null) {
            page = billRepository.findByHouseholdIdAndStatus(householdId, status, pageable);
        } else if (startDate != null && endDate != null) {
            page = billRepository.findByHouseholdIdAndDateRange(householdId, startDate, endDate, pageable);
        } else {
            page = billRepository.findByHouseholdId(householdId, pageable);
        }

        return page.map(this::toResponse);
    }

    @Transactional
    public BillDto.Response update(User user, Long id, BillDto.UpdateRequest request) {
        Bill bill = getEntityByIdAndCheckPermission(user, id);

        if (bill.getStatus() != BillStatus.PENDING_CONFIRMATION) {
            throw new BusinessException("仅待确认状态的账单允许修改基本信息");
        }

        if (request.getPeriodStart() != null) {
            bill.setPeriodStart(request.getPeriodStart());
        }
        if (request.getPeriodEnd() != null) {
            bill.setPeriodEnd(request.getPeriodEnd());
        }
        if (request.getTotalAmount() != null) {
            bill.setTotalAmount(request.getTotalAmount());
        }
        if (request.getDueDate() != null) {
            bill.setDueDate(request.getDueDate());
        }

        Bill saved = billRepository.save(bill);
        return toResponse(saved);
    }

    @Transactional
    public BillDto.Response transitionStatus(User user, Long id, BillDto.TransitionRequest request) {
        Bill bill = getEntityByIdAndCheckPermission(user, id);

        if (request.getTargetStatus() == BillStatus.PENDING_PAYMENT) {
            amountValidationService.validateForPaymentTransition(bill);
        }

        String reason = request.getReason() != null ? request.getReason() : "手动状态转换";
        Bill updated = stateMachineService.transition(user, bill, request.getTargetStatus(), reason);

        if (request.getTargetStatus() == BillStatus.PENDING_PAYMENT) {
            List<BillItem> items = billItemRepository.findByBillId(updated.getId());
            notificationService.notifyBillCreated(updated, items);
        }

        return toResponse(updated);
    }

    public List<BillDto.StatusHistoryResponse> getStatusHistory(User user, Long id) {
        getEntityByIdAndCheckPermission(user, id);
        List<BillStatusHistory> history = stateMachineService.getStatusHistory(id);
        return history.stream()
                .map(BillDto.StatusHistoryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(User user, Long id) {
        Bill bill = getEntityByIdAndCheckPermission(user, id);

        if (bill.getStatus() != BillStatus.PENDING_CONFIRMATION) {
            throw new BusinessException("仅待确认状态的账单允许删除");
        }

        List<BillStatusHistory> history = billStatusHistoryRepository.findByBillIdOrderByOperatedAtDesc(id);
        billStatusHistoryRepository.deleteAll(history);

        List<BillItem> items = billItemRepository.findByBillId(id);
        billItemRepository.deleteAll(items);
        billRepository.delete(bill);
    }

    public List<BillDto.StatusHistoryResponse> getAbnormalTransitions(User user, LocalDateTime since) {
        List<BillStatusHistory> abnormal = stateMachineService.findAbnormalTransitions(since);
        return abnormal.stream()
                .map(BillDto.StatusHistoryResponse::from)
                .collect(Collectors.toList());
    }

    public List<BillDto.AmountAuditLogResponse> getAmountAuditLogs(User user, Long billId) {
        getEntityByIdAndCheckPermission(user, billId);
        return amountValidationService.getAuditLogsForBill(billId).stream()
                .map(BillDto.AmountAuditLogResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public int migrateHistoricalBills(User user) {
        if (!hasAdminPermission(user)) {
            throw new BusinessException("无权限执行数据迁移");
        }
        return billRepository.migrateNullRuleVersion(stateMachineConfig.getCurrentRuleVersion());
    }

    private BillDto.Response toResponse(Bill bill) {
        List<BillItem> items = billItemRepository.findByBillId(bill.getId());
        List<BillItemDto.Response> itemResponses = items.stream()
                .map(this::toBillItemResponse)
                .collect(Collectors.toList());

        Set<BillStatus> allowedTransitions = stateMachineService.getAllowedTransitions(bill.getId());

        return BillDto.Response.builder()
                .id(bill.getId())
                .householdId(bill.getHousehold().getId())
                .householdName(bill.getHousehold().getName())
                .periodStart(bill.getPeriodStart())
                .periodEnd(bill.getPeriodEnd())
                .dueDate(bill.getDueDate())
                .totalAmount(bill.getTotalAmount())
                .status(bill.getStatus())
                .ruleVersion(bill.getRuleVersion())
                .allowedTransitions(allowedTransitions)
                .createdAt(bill.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    private BillItemDto.Response toBillItemResponse(BillItem item) {
        return BillItemDto.Response.builder()
                .id(item.getId())
                .billId(item.getBill().getId())
                .roomId(item.getRoom().getId())
                .roomName(item.getRoom().getName())
                .baseShare(item.getBaseShare())
                .acShare(item.getAcShare())
                .publicShare(item.getPublicShare())
                .totalDue(item.getTotalDue())
                .isPaid(item.getIsPaid())
                .paidAt(item.getPaidAt())
                .isPublicArea(item.getIsPublicArea())
                .allocationType(item.getAllocationType())
                .calculationDetails(item.getCalculationDetails())
                .hasRoundingAdjustment(item.getHasRoundingAdjustment())
                .roundingAdjustmentAmount(item.getRoundingAdjustmentAmount())
                .build();
    }

    private boolean hasAdminPermission(User user) {
        return user != null && user.getId() != null;
    }
}
