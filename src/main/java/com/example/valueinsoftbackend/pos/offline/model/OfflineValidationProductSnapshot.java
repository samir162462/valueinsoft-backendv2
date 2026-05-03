package com.example.valueinsoftbackend.pos.offline.model;

import java.math.BigDecimal;

public record OfflineValidationProductSnapshot(
        Long productId,
        String barcode,
        BigDecimal retailPrice,
        BigDecimal lowestPrice,
        boolean availableInBranch,
        boolean active
) {
}
