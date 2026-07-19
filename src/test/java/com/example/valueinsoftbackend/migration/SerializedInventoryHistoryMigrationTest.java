package com.example.valueinsoftbackend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializedInventoryHistoryMigrationTest {

    @Test
    void backfillCreatesRealLedgerRowsOnlyWhenNoAuthoritativeAcquisitionExists() throws Exception {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V153__serialized_inventory_history_ledger_backfill.sql"
        );

        assertTrue(migration.exists());
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .toUpperCase();

        assertTrue(sql.contains("BACKFILL_SERIALIZED_INVENTORY_HISTORY_FOR_TENANT"));
        assertTrue(sql.contains("INSERT INTO %I.INVENTORY_STOCK_LEDGER"));
        assertTrue(sql.contains("LEDGER.PRODUCT_UNIT_ID = UNIT.PRODUCT_UNIT_ID"));
        assertTrue(sql.contains("LEDGER.REFERENCE_ID = UNIT.PURCHASE_REFERENCE_ID"));
        assertTrue(sql.contains("ON CONFLICT DO NOTHING"));
        assertFalse(sql.contains("-UNIT.PRODUCT_UNIT_ID"));
    }
}
