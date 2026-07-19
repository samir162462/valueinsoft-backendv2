package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InventoryDamageRequest(
        @Positive int companyId,
        @Positive int branchId,
        @Positive long productId,
        @Positive int quantity,
        @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 255) String reason,
        @NotBlank @Size(max = 120) String damagedBy,
        @PositiveOrZero Integer liabilityAmount,
        @NotBlank @Size(max = 160) String idempotencyKey
) {
}
