package com.example.valueinsoftbackend.Model.Inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventoryReconciliationSnapshot(
        int companyId,
        int branchId,
        Instant generatedAt,
        String status,
        List<String> missingObjects,
        Summary summary,
        List<Row> discrepancies,
        boolean truncated
) {
    public record Summary(
            long productKeys,
            long balanceVsLegacyLedgerDifferences,
            long balanceVsModernMovementDifferences,
            long serializedUnitDifferences,
            long discrepancyRows
    ) {
        public boolean hasDrift() {
            return discrepancyRows > 0;
        }
    }

    public record Row(
            long productId,
            String productName,
            BigDecimal balanceQuantity,
            BigDecimal reservedQuantity,
            BigDecimal legacyLedgerQuantity,
            BigDecimal modernMovementQuantity,
            long availableUnits,
            long reservedUnits,
            long otherUnits,
            BigDecimal balanceVsLegacyLedgerDelta,
            BigDecimal balanceVsModernMovementDelta,
            BigDecimal balanceVsSellableUnitsDelta,
            List<String> differenceTypes
    ) {
    }
}
