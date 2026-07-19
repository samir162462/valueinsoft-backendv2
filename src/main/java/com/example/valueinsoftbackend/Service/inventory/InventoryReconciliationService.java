package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryReconciliation.DbInventoryReconciliationReadModels;
import com.example.valueinsoftbackend.Model.Inventory.InventoryReconciliationSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class InventoryReconciliationService {

    private final DbInventoryReconciliationReadModels readModels;

    public InventoryReconciliationService(DbInventoryReconciliationReadModels readModels) {
        this.readModels = readModels;
    }

    @Transactional(readOnly = true)
    public InventoryReconciliationSnapshot snapshot(int companyId, int branchId, int limit) {
        List<String> missingObjects = readModels.missingObjects(companyId);
        if (!missingObjects.isEmpty()) {
            return new InventoryReconciliationSnapshot(
                    companyId,
                    branchId,
                    Instant.now(),
                    "SCHEMA_DRIFT",
                    List.copyOf(missingObjects),
                    null,
                    List.of(),
                    false
            );
        }

        InventoryReconciliationSnapshot.Summary summary = readModels.summary(companyId, branchId);
        List<InventoryReconciliationSnapshot.Row> discrepancies = summary.hasDrift()
                ? readModels.discrepancies(companyId, branchId, limit)
                : List.of();
        return new InventoryReconciliationSnapshot(
                companyId,
                branchId,
                Instant.now(),
                summary.hasDrift() ? "DRIFT" : "MATCHED",
                List.of(),
                summary,
                discrepancies,
                summary.discrepancyRows() > discrepancies.size()
        );
    }
}
