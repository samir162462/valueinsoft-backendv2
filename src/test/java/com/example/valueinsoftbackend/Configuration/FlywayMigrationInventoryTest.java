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

    @Test
    void inventoryDynamicPricingFoundationMigrationExistsAndUsesDecimalMoneyTypes() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V101__inventory_dynamic_pricing_foundation.sql"
        );

        assertTrue(migration.exists(), "Missing inventory dynamic pricing foundation migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("CREATE_INVENTORY_PRICING_ENGINE_TABLES_FOR_TENANT"));
        assertTrue(sql.contains("INVENTORY_DYNAMIC_PRICING_POLICY"));
        assertTrue(sql.contains("INVENTORY_PRODUCT_PRICE_HISTORY"));
        assertTrue(sql.contains("INVENTORY_PRICE_RECOMMENDATION_RUN"));
        assertTrue(sql.contains("INVENTORY_PRICE_RECOMMENDATION_ITEM"));
        assertTrue(sql.contains("INVENTORY_PRICE_ADJUSTMENT_BATCH"));
        assertTrue(sql.contains("INVENTORY_PRICE_ADJUSTMENT_ITEM"));
        assertTrue(sql.contains("INVENTORY_PRICING_AUDIT_LOG"));
        assertTrue(sql.contains("INVENTORY.PRICING.VIEW"));
        assertTrue(sql.contains("NUMERIC(19,4)"), "Dynamic pricing monetary columns must use NUMERIC(19,4)");
        assertFalse(sql.contains(" FLOAT"), "Dynamic pricing migrations must not use FLOAT for monetary values");
        assertFalse(sql.contains(" REAL"), "Dynamic pricing migrations must not use REAL for monetary values");
    }

    @Test
    void inventoryProductReceiptIdempotencyMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V110__inventory_product_receipt_idempotency.sql"
        );

        assertTrue(migration.exists(), "Missing inventory product receipt idempotency migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertTrue(sql.contains("INVENTORY_OPERATION_IDEMPOTENCY"));
        assertTrue(sql.contains("IDEMPOTENCY_KEY"));
        assertTrue(sql.contains("REQUEST_HASH"));
        assertTrue(sql.contains("RESPONSE_PAYLOAD JSONB"));
        assertTrue(sql.contains("UNIQUE INDEX"));
        assertTrue(sql.contains("INVENTORY_STOCK_LEDGER"));
    }

    @Test
    void billingBalanceFirstFoundationMigrationExistsAndUsesAllocationModel() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V113__billing_balance_first_foundation.sql"
        );

        assertTrue(migration.exists(), "Missing billing balance-first foundation migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        String billingPaymentsDefinition = sql.substring(
                sql.indexOf("CREATE TABLE IF NOT EXISTS PUBLIC.BILLING_PAYMENTS"),
                sql.indexOf("CREATE INDEX IF NOT EXISTS IDX_BILLING_PAYMENTS_COMPANY_SOURCE_CREATED")
        );

        assertTrue(sql.contains("BILLING_ACCOUNT_LEDGER"));
        assertTrue(sql.contains("BILLING_PAYMENTS"));
        assertTrue(sql.contains("BILLING_PAYMENT_ALLOCATIONS"));
        assertTrue(sql.contains("BILLING_PROVIDER_CHECKOUT_OUTBOX"));
        assertTrue(sql.contains("UQ_BILLING_ACCOUNTS_COMPANY_CURRENCY"));
        assertTrue(sql.contains("AVAILABLE_BALANCE NUMERIC(12, 2)"));
        assertTrue(sql.contains("PAID_AMOUNT NUMERIC(12, 2)"));
        assertTrue(sql.contains("CHECKOUT_PENDING"));
        assertTrue(sql.contains("FUNDING_SOURCE"));
        assertTrue(sql.contains("CREDIT_REASON"));
        assertTrue(sql.contains("UX_BILLING_PAYMENT_ATTEMPTS_ACTIVE_INVOICE_PROVIDER"));
        assertFalse(
                billingPaymentsDefinition.contains("BILLING_INVOICE_ID"),
                "billing_payments must not directly link to invoices; allocations are the source of truth"
        );
        assertFalse(sql.contains(" FLOAT"), "Billing balance-first migration must not use FLOAT for money");
        assertFalse(sql.contains(" REAL"), "Billing balance-first migration must not use REAL for money");
    }

    @Test
    void billingBalanceFinanceMappingMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V114__billing_balance_finance_mapping.sql"
        );

        assertTrue(migration.exists(), "Missing billing balance finance mapping migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        assertTrue(sql.contains("PAYMENT.CUSTOMER_DEPOSITS"));
        assertTrue(sql.contains("FINANCE_ACCOUNT_MAPPING"));
        assertTrue(sql.contains("ACCOUNT_CODE = '2300'"));
        assertTrue(sql.contains("NOT EXISTS"));
    }

    @Test
    void billingBalancePlatformCapabilityMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V115__billing_balance_platform_capabilities.sql"
        );

        assertTrue(migration.exists(), "Missing billing balance platform capability migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        assertTrue(sql.contains("PLATFORM.BILLING.BALANCE.WRITE"));
        assertTrue(sql.contains("SUPPORTADMIN"));
        assertTrue(sql.contains("ROLE_GRANTS"));
    }

    @Test
    void billingReconciliationAndRefundsMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V118__billing_reconciliation_and_refunds.sql"
        );

        assertTrue(migration.exists(), "Missing billing reconciliation and refunds migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        assertTrue(sql.contains("PROVIDER_GROSS_AMOUNT NUMERIC(12, 2)"));
        assertTrue(sql.contains("PROVIDER_FEE_AMOUNT NUMERIC(12, 2)"));
        assertTrue(sql.contains("PROVIDER_NET_AMOUNT NUMERIC(12, 2)"));
        assertTrue(sql.contains("SETTLEMENT_DESTINATION"));
        assertTrue(sql.contains("RECONCILIATION_STATUS"));
        assertFalse(sql.contains(" FLOAT"), "Billing reconciliation migration must not use FLOAT for money");
        assertFalse(sql.contains(" REAL"), "Billing reconciliation migration must not use REAL for money");
    }

    @Test
    void legacyBillingSubscriptionRetirementMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V119__retire_legacy_billing_subscription_artifacts.sql"
        );

        assertTrue(migration.exists(), "Missing legacy billing subscription retirement migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        assertTrue(sql.contains("DROP TABLE IF EXISTS PUBLIC.\"COMPANYSUBSCRIPTION\""));
        assertTrue(sql.contains("DROP COLUMN IF EXISTS LEGACY_SUBSCRIPTION_ID"));
    }

    @Test
    void billingCheckoutOutboxRepairMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V120__repair_billing_checkout_outbox.sql"
        );

        assertTrue(migration.exists(), "Missing billing checkout outbox repair migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        assertTrue(sql.contains("BILLING_PROVIDER_CHECKOUT_OUTBOX"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS COMPANY_ID"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS CHECKOUT_REFERENCE"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS PUBLIC.BILLING_PROVIDER_CHECKOUT_OUTBOX"));
        assertTrue(sql.contains("TRG_BILLING_PROVIDER_CHECKOUT_OUTBOX_SET_UPDATED_AT"));
        assertFalse(sql.contains(" FLOAT"), "Billing checkout outbox repair migration must not use FLOAT for money");
        assertFalse(sql.contains(" REAL"), "Billing checkout outbox repair migration must not use REAL for money");
    }

    @Test
    void billingBalanceFirstSchemaRepairMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V121__repair_billing_balance_first_schema.sql"
        );

        assertTrue(migration.exists(), "Missing billing balance-first schema repair migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS AVAILABLE_BALANCE"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS PAID_AMOUNT"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS PUBLIC.BILLING_ACCOUNT_LEDGER"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS PUBLIC.BILLING_PAYMENTS"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS PUBLIC.BILLING_PAYMENT_ALLOCATIONS"));
        assertTrue(sql.contains("PROVIDER_GROSS_AMOUNT NUMERIC(12, 2)"));
        assertFalse(sql.contains(" FLOAT"), "Billing balance-first schema repair migration must not use FLOAT for money");
        assertFalse(sql.contains(" REAL"), "Billing balance-first schema repair migration must not use REAL for money");
    }

    @Test
    void billingCreditExpenseFinanceMappingMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V116__billing_credit_expense_finance_mapping.sql"
        );

        assertTrue(migration.exists(), "Missing billing credit expense finance mapping migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        assertTrue(sql.contains("BILLING CREDITS EXPENSE"));
        assertTrue(sql.contains("PAYMENT.BILLING_CREDIT_EXPENSE"));
        assertTrue(sql.contains("ACCOUNT_CODE = '6600'"));
        assertTrue(sql.contains("FINANCE_ACCOUNT_MAPPING"));
        assertTrue(sql.contains("NOT EXISTS"));
    }
}
