package com.example.valueinsoftbackend.Configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FlywayMigrationInventoryTest {

    @Test
    void configurationMigrationSetExistsThroughV36() {
        String[] migrations = {
                "db/migration/V6__configuration_platform_auth_foundation.sql",
                "db/migration/V7__configuration_platform_auth_foundation_seed.sql",
                "db/migration/V8__configuration_commercial_template_foundation.sql",
                "db/migration/V9__configuration_commercial_template_foundation_seed.sql",
                "db/migration/V10__configuration_tenant_state_foundation.sql",
                "db/migration/V11__configuration_tenant_state_bootstrap.sql",
                "db/migration/V12__configuration_scope_aware_assignments.sql",
                "db/migration/V13__configuration_capability_expansion_seed.sql",
                "db/migration/V14__configuration_inventory_capability_expansion_seed.sql",
                "db/migration/V15__configuration_pos_sale_capability_expansion_seed.sql",
                "db/migration/V16__configuration_pos_shift_capability_expansion_seed.sql",
                "db/migration/V17__configuration_inventory_repair_capability_expansion_seed.sql",
                "db/migration/V18__configuration_management_portal_branch_manager_grants.sql",
                "db/migration/V19__platform_admin_foundation.sql",
                "db/migration/V20__platform_admin_capability_seed.sql",
                "db/migration/V21__platform_admin_alert_acknowledgment_foundation.sql",
                "db/migration/V22__platform_admin_alert_scope_and_notification_outbox.sql",
                "db/migration/V23__platform_admin_default_user_seed.sql",
                "db/migration/V24__billing_core_foundation.sql",
                "db/migration/V25__package_plan_pricing_admin_fields.sql",
                "db/migration/V26__package_plan_feature_catalog_seed.sql",
                "db/migration/V27__package_plan_feature_policy_defaults.sql",
                "db/migration/V28__business_package_category_catalog.sql",
                "db/migration/V29__business_package_category_catalog_seed.sql",
                "db/migration/V30__inventory_company_catalog_foundation.sql",
                "db/migration/V31__inventory_legacy_transaction_fk_cleanup.sql",
                "db/migration/V32__inventory_legacy_product_mapping.sql",
                "db/migration/V33__inventory_template_attribute_foundation.sql",
                "db/migration/V34__inventory_move_modern_runtime_to_tenant_schema.sql",
                "db/migration/V35__inventory_pricing_and_uom_foundation.sql",
                "db/migration/V36__inventory_ledger_compatibility_metadata.sql"
        };

        for (String migration : migrations) {
            assertTrue(
                    new ClassPathResource(migration).exists(),
                    "Missing required migration resource: " + migration
            );
        }
    }

    @Test
    void financeFoundationMigrationExistsAndAvoidsFloatingPointMoneyTypes() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V49__finance_fiscal_calendar_and_accounts.sql"
        );

        assertTrue(migration.exists(), "Missing finance foundation migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("FINANCE_FISCAL_YEAR"));
        assertTrue(sql.contains("FINANCE_FISCAL_PERIOD"));
        assertTrue(sql.contains("FINANCE_ACCOUNT"));
        assertTrue(sql.contains("FINANCE_ACCOUNT_MAPPING"));
        assertTrue(sql.contains("FINANCE_TAX_CODE"));
        assertFalse(sql.contains(" FLOAT"), "Finance migrations must not use FLOAT for monetary values");
        assertFalse(sql.contains(" REAL"), "Finance migrations must not use REAL for monetary values");
    }

    @Test
    void financeJournalCoreMigrationExistsAndUsesDecimalMoneyTypes() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V50__finance_journal_core.sql"
        );

        assertTrue(migration.exists(), "Missing finance journal core migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("FINANCE_JOURNAL_SEQUENCE"));
        assertTrue(sql.contains("FINANCE_POSTING_BATCH"));
        assertTrue(sql.contains("FINANCE_JOURNAL_ENTRY"));
        assertTrue(sql.contains("FINANCE_POSTING_REQUEST"));
        assertTrue(sql.contains("FINANCE_JOURNAL_LINE"));
        assertTrue(sql.contains("FINANCE_TAX_LINE"));
        assertTrue(sql.contains("DECIMAL(19,4)"), "Journal core must use DECIMAL(19,4) for money");
        assertTrue(sql.contains("DECIMAL(19,8)"), "Journal core must use DECIMAL(19,8) for exchange rates");
        assertFalse(sql.contains(" FLOAT"), "Finance migrations must not use FLOAT for monetary values");
        assertFalse(sql.contains(" REAL"), "Finance migrations must not use REAL for monetary values");
    }

    @Test
    void financeReportingCloseAuditMigrationExistsAndUsesDecimalMoneyTypes() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V51__finance_reporting_reconciliation_close_audit.sql"
        );

        assertTrue(migration.exists(), "Missing finance reporting/reconciliation/close/audit migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("FINANCE_ACCOUNT_BALANCE"));
        assertTrue(sql.contains("FINANCE_TRIAL_BALANCE_SNAPSHOT"));
        assertTrue(sql.contains("FINANCE_RECONCILIATION_RUN"));
        assertTrue(sql.contains("FINANCE_RECONCILIATION_ITEM"));
        assertTrue(sql.contains("FINANCE_PERIOD_CLOSE_RUN"));
        assertTrue(sql.contains("FINANCE_AUDIT_EVENT"));
        assertTrue(sql.contains("DECIMAL(19,4)"), "Finance reporting/close migration must use DECIMAL(19,4) for money");
        assertFalse(sql.contains(" FLOAT"), "Finance migrations must not use FLOAT for monetary values");
        assertFalse(sql.contains(" REAL"), "Finance migrations must not use REAL for monetary values");
    }

    @Test
    void financeTrialBalanceSnapshotMetadataMigrationExistsAndAvoidsFloatingPointMoneyTypes() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V52__finance_trial_balance_snapshot_metadata.sql"
        );

        assertTrue(migration.exists(), "Missing finance trial balance snapshot metadata migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("FINANCE_TRIAL_BALANCE_SNAPSHOT"));
        assertTrue(sql.contains("CURRENCY_CODE"));
        assertTrue(sql.contains("BALANCE_ROW_COUNT"));
        assertFalse(sql.contains(" FLOAT"), "Finance migrations must not use FLOAT for monetary values");
        assertFalse(sql.contains(" REAL"), "Finance migrations must not use REAL for monetary values");
    }

    @Test
    void financePostingRequestOrchestrationMigrationExistsAndAvoidsFloatingPointMoneyTypes() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V53__finance_posting_request_orchestration_metadata.sql"
        );

        assertTrue(migration.exists(), "Missing finance posting request orchestration migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("FINANCE_POSTING_REQUEST"));
        assertTrue(sql.contains("POSTING_DATE"));
        assertTrue(sql.contains("FISCAL_PERIOD_ID"));
        assertTrue(sql.contains("REQUEST_PAYLOAD"));
        assertFalse(sql.contains(" FLOAT"), "Finance migrations must not use FLOAT for monetary values");
        assertFalse(sql.contains(" REAL"), "Finance migrations must not use REAL for monetary values");
    }

    @Test
    void financeReconciliationSourceItemsMigrationExistsAndAvoidsFloatingPointMoneyTypes() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V54__finance_reconciliation_source_items.sql"
        );

        assertTrue(migration.exists(), "Missing finance reconciliation source items migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("FINANCE_RECONCILIATION_SOURCE_ITEM"));
        assertTrue(sql.contains("FINANCE_RECONCILIATION_ITEM"));
        assertTrue(sql.contains("RECONCILIATION_SOURCE_ITEM_ID"));
        assertTrue(sql.contains("DECIMAL(19,4)"), "Reconciliation source items must use DECIMAL(19,4) for money");
        assertFalse(sql.contains(" FLOAT"), "Finance migrations must not use FLOAT for monetary values");
        assertFalse(sql.contains(" REAL"), "Finance migrations must not use REAL for monetary values");
    }
}
