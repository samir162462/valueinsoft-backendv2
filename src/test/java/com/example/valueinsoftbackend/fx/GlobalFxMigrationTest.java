package com.example.valueinsoftbackend.fx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalFxMigrationTest {

    @Test
    void migrationDefinesGlobalRateLockCompanyAndTenantTables() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V102__global_fx_deepseek_rate_foundation.sql"));

        assertTrue(sql.contains("public.global_scheduler_lock"));
        assertTrue(sql.contains("public.global_fx_rate_snapshot"));
        assertTrue(sql.contains("uq_global_fx_rate_snapshot_scheduled_valid_week"));
        assertTrue(sql.contains("public.company_fx_pricing_config"));
        assertTrue(sql.contains("public.company_fx_effective_rate"));
        assertTrue(sql.contains("inventory_fx_product_impact"));
        assertTrue(sql.contains("fx_pricing_enabled"));
        assertTrue(sql.contains("platform.fx.read"));
        assertTrue(sql.contains("platform.fx.refresh"));
    }
}
