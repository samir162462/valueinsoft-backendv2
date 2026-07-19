package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryReconciliation.DbInventoryReconciliationReadModels;
import com.example.valueinsoftbackend.Model.Inventory.InventoryReconciliationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReconciliationServiceTest {

    @Test
    void schemaDriftStopsBeforeTenantQueries() {
        DbInventoryReconciliationReadModels readModels = mock(DbInventoryReconciliationReadModels.class);
        when(readModels.missingObjects(10)).thenReturn(List.of("inventory_stock_movement"));
        InventoryReconciliationService service = new InventoryReconciliationService(readModels);

        InventoryReconciliationSnapshot snapshot = service.snapshot(10, 20, 100);

        assertEquals("SCHEMA_DRIFT", snapshot.status());
        assertEquals(List.of("inventory_stock_movement"), snapshot.missingObjects());
        assertNull(snapshot.summary());
        verify(readModels, never()).summary(10, 20);
    }

    @Test
    void matchedSnapshotDoesNotLoadDifferenceRows() {
        DbInventoryReconciliationReadModels readModels = mock(DbInventoryReconciliationReadModels.class);
        when(readModels.missingObjects(10)).thenReturn(List.of());
        when(readModels.summary(10, 20)).thenReturn(new InventoryReconciliationSnapshot.Summary(12, 0, 0, 0, 0));
        InventoryReconciliationService service = new InventoryReconciliationService(readModels);

        InventoryReconciliationSnapshot snapshot = service.snapshot(10, 20, 100);

        assertEquals("MATCHED", snapshot.status());
        assertFalse(snapshot.truncated());
        verify(readModels, never()).discrepancies(10, 20, 100);
    }

    @Test
    void driftSnapshotIsBoundedAndReportsTruncation() {
        DbInventoryReconciliationReadModels readModels = mock(DbInventoryReconciliationReadModels.class);
        when(readModels.missingObjects(10)).thenReturn(List.of());
        when(readModels.summary(10, 20)).thenReturn(new InventoryReconciliationSnapshot.Summary(12, 2, 3, 1, 4));
        when(readModels.discrepancies(10, 20, 2)).thenReturn(List.of(
                mock(InventoryReconciliationSnapshot.Row.class),
                mock(InventoryReconciliationSnapshot.Row.class)
        ));
        InventoryReconciliationService service = new InventoryReconciliationService(readModels);

        InventoryReconciliationSnapshot snapshot = service.snapshot(10, 20, 2);

        assertEquals("DRIFT", snapshot.status());
        assertEquals(2, snapshot.discrepancies().size());
        assertEquals(true, snapshot.truncated());
    }
}
