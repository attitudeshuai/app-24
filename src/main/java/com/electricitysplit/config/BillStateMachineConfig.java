package com.electricitysplit.config;

import com.electricitysplit.entity.BillStatus;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BillStateMachineConfig {

    public static final String CURRENT_RULE_VERSION = "v1.0";

    public static final int DEFAULT_OVERDUE_DAYS_AFTER_PERIOD_END = 15;

    private static final Map<String, Map<BillStatus, Set<BillStatus>>> VERSIONED_TRANSITIONS = new HashMap<>();

    static {
        Map<BillStatus, Set<BillStatus>> v1Rules = new EnumMap<>(BillStatus.class);
        v1Rules.put(BillStatus.PENDING_CONFIRMATION, EnumSet.of(
                BillStatus.PENDING_PAYMENT
        ));
        v1Rules.put(BillStatus.PENDING_PAYMENT, EnumSet.of(
                BillStatus.PAID,
                BillStatus.OVERDUE
        ));
        v1Rules.put(BillStatus.PAID, EnumSet.noneOf(BillStatus.class));
        v1Rules.put(BillStatus.OVERDUE, EnumSet.of(
                BillStatus.PAID
        ));
        VERSIONED_TRANSITIONS.put("v1.0", v1Rules);
    }

    public String getCurrentRuleVersion() {
        return CURRENT_RULE_VERSION;
    }

    public boolean canTransition(String ruleVersion, BillStatus from, BillStatus to) {
        Map<BillStatus, Set<BillStatus>> rules = VERSIONED_TRANSITIONS.get(ruleVersion);
        if (rules == null) {
            rules = VERSIONED_TRANSITIONS.get(CURRENT_RULE_VERSION);
        }
        Set<BillStatus> allowed = rules.get(from);
        return allowed != null && allowed.contains(to);
    }

    public Set<BillStatus> getAllowedTransitions(String ruleVersion, BillStatus from) {
        Map<BillStatus, Set<BillStatus>> rules = VERSIONED_TRANSITIONS.get(ruleVersion);
        if (rules == null) {
            rules = VERSIONED_TRANSITIONS.get(CURRENT_RULE_VERSION);
        }
        Set<BillStatus> allowed = rules.get(from);
        return allowed != null ? EnumSet.copyOf(allowed) : EnumSet.noneOf(BillStatus.class);
    }

    public Set<String> getAvailableRuleVersions() {
        return VERSIONED_TRANSITIONS.keySet();
    }

    public int getOverdueDaysAfterPeriodEnd() {
        return DEFAULT_OVERDUE_DAYS_AFTER_PERIOD_END;
    }

    public boolean isTerminalStatus(BillStatus status) {
        return status == BillStatus.PAID;
    }
}
