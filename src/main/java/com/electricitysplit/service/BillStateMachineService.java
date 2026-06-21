package com.electricitysplit.service;

import com.electricitysplit.config.BillStateMachineConfig;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillStatus;
import com.electricitysplit.entity.BillStatusHistory;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.BillRepository;
import com.electricitysplit.repository.BillStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillStateMachineService {

    private final BillRepository billRepository;
    private final BillStatusHistoryRepository billStatusHistoryRepository;
    private final BillStateMachineConfig stateMachineConfig;

    @Transactional
    public Bill transition(User operator, Long billId, BillStatus targetStatus, String reason) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BusinessException("账单不存在"));
        return transition(operator, bill, targetStatus, reason, false);
    }

    @Transactional
    public Bill transition(User operator, Bill bill, BillStatus targetStatus, String reason) {
        return transition(operator, bill, targetStatus, reason, false);
    }

    @Transactional
    public Bill autoTransition(Bill bill, BillStatus targetStatus, String reason) {
        return transition(null, bill, targetStatus, reason, true);
    }

    private Bill transition(User operator, Bill bill, BillStatus targetStatus, String reason, boolean isAuto) {
        BillStatus currentStatus = bill.getStatus();
        String ruleVersion = bill.getRuleVersion() != null ? bill.getRuleVersion() : stateMachineConfig.getCurrentRuleVersion();

        if (currentStatus == targetStatus) {
            log.info("账单 {} 状态已是 {}, 无需转换", bill.getId(), targetStatus);
            return bill;
        }

        if (!stateMachineConfig.canTransition(ruleVersion, currentStatus, targetStatus)) {
            throw new BusinessException(
                    String.format("不允许的状态转换: %s -> %s (规则版本: %s)", currentStatus, targetStatus, ruleVersion)
            );
        }

        String operatorName = isAuto ? "SYSTEM" : (operator != null ? operator.getUsername() : "UNKNOWN");

        BillStatusHistory history = BillStatusHistory.builder()
                .bill(bill)
                .fromStatus(currentStatus)
                .toStatus(targetStatus)
                .operator(operator)
                .operatorName(operatorName)
                .reason(reason)
                .ruleVersion(ruleVersion)
                .isAuto(isAuto)
                .build();
        billStatusHistoryRepository.save(history);

        bill.setStatusInternal(targetStatus);
        Bill saved = billRepository.save(bill);

        log.info("账单 {} 状态转换成功: {} -> {}, 操作人: {}, 原因: {}",
                bill.getId(), currentStatus, targetStatus, operatorName, reason);

        return saved;
    }

    @Transactional(readOnly = true)
    public Set<BillStatus> getAllowedTransitions(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BusinessException("账单不存在"));
        String ruleVersion = bill.getRuleVersion() != null ? bill.getRuleVersion() : stateMachineConfig.getCurrentRuleVersion();
        return stateMachineConfig.getAllowedTransitions(ruleVersion, bill.getStatus());
    }

    @Transactional(readOnly = true)
    public boolean canTransition(Long billId, BillStatus targetStatus) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BusinessException("账单不存在"));
        String ruleVersion = bill.getRuleVersion() != null ? bill.getRuleVersion() : stateMachineConfig.getCurrentRuleVersion();
        return stateMachineConfig.canTransition(ruleVersion, bill.getStatus(), targetStatus);
    }

    @Transactional(readOnly = true)
    public List<BillStatusHistory> getStatusHistory(Long billId) {
        return billStatusHistoryRepository.findByBillIdOrderByOperatedAtDesc(billId);
    }

    @Transactional(readOnly = true)
    public List<BillStatusHistory> findAbnormalTransitions(LocalDateTime since) {
        return billStatusHistoryRepository.findAbnormalTransitions(
                stateMachineConfig.getCurrentRuleVersion(),
                since != null ? since : LocalDateTime.now().minusDays(30)
        );
    }
}
