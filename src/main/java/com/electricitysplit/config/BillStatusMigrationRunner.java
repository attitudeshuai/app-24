package com.electricitysplit.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class BillStatusMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        migrateLegacyStatusValues();
        migrateNullRuleVersions();
    }

    private void migrateLegacyStatusValues() {
        try {
            Integer draftCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bills WHERE status = 'Draft'", Integer.class);
            Integer sentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bills WHERE status = 'Sent'", Integer.class);
            Integer paidCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bills WHERE status = 'Paid'", Integer.class);

            int totalLegacy = (draftCount != null ? draftCount : 0)
                    + (sentCount != null ? sentCount : 0)
                    + (paidCount != null ? paidCount : 0);

            if (totalLegacy > 0) {
                log.info("检测到 {} 条旧版账单状态记录，开始迁移...", totalLegacy);

                int draftMigrated = jdbcTemplate.update(
                        "UPDATE bills SET status = 'PENDING_CONFIRMATION' WHERE status = 'Draft'");
                int sentMigrated = jdbcTemplate.update(
                        "UPDATE bills SET status = 'PENDING_PAYMENT' WHERE status = 'Sent'");
                int paidMigrated = jdbcTemplate.update(
                        "UPDATE bills SET status = 'PAID' WHERE status = 'Paid'");

                log.info("旧状态迁移完成: Draft->PENDING_CONFIRMATION: {} 条, " +
                                "Sent->PENDING_PAYMENT: {} 条, Paid->PAID: {} 条",
                        draftMigrated, sentMigrated, paidMigrated);
            } else {
                log.info("未检测到旧版账单状态记录，跳过迁移");
            }
        } catch (Exception e) {
            log.warn("执行旧状态迁移时出现异常（可能是首次运行表不存在）: {}", e.getMessage());
        }
    }

    private void migrateNullRuleVersions() {
        try {
            Integer nullVersionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bills WHERE rule_version IS NULL OR rule_version = ''", Integer.class);

            if (nullVersionCount != null && nullVersionCount > 0) {
                log.info("检测到 {} 条账单缺少规则版本，开始补全...", nullVersionCount);
                int migrated = jdbcTemplate.update(
                        "UPDATE bills SET rule_version = 'v1.0' WHERE rule_version IS NULL OR rule_version = ''");
                log.info("规则版本补全完成: {} 条", migrated);
            }
        } catch (Exception e) {
            log.warn("执行规则版本补全时出现异常（可能是首次运行列不存在）: {}", e.getMessage());
        }
    }
}
