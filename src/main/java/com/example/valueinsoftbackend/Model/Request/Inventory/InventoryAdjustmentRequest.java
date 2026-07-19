package com.example.valueinsoftbackend.Model.Request.Inventory;

import com.example.valueinsoftbackend.Model.Inventory.InventoryAdjustmentReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InventoryAdjustmentRequest(
        @Positive int companyId,
        @Positive int branchId,
        @Positive long productId,
        @NotNull Integer quantityDelta,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull InventoryAdjustmentReason reasonCode,
        @Size(max = 255) String note,
        @NotBlank @Size(max = 160) String idempotencyKey
) {
}
