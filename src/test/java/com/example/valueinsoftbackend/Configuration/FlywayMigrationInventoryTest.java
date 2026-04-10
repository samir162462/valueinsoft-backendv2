package com.example.valueinsoftbackend.Configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationInventoryTest {

    @Test
    void configurationMigrationSetExistsThroughV29() {
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
                "db/migration/V29__business_package_category_catalog_seed.sql"
        };

        for (String migration : migrations) {
            assertTrue(
                    new ClassPathResource(migration).exists(),
                    "Missing required migration resource: " + migration
            );
        }
    }
}
