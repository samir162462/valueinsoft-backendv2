package com.example.valueinsoftbackend.Configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationInventoryTest {

    @Test
    void configurationMigrationSetExistsThroughV17() {
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
                "db/migration/V17__configuration_inventory_repair_capability_expansion_seed.sql"
        };

        for (String migration : migrations) {
            assertTrue(
                    new ClassPathResource(migration).exists(),
                    "Missing required migration resource: " + migration
            );
        }
    }
}
