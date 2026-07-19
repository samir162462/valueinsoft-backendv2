package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InventoryDamageReversalRequest(
        @Positive int companyId,
        @Positive int branchId,
        @Positive long damageId,
        @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 255) String reason,
        @NotBlank @Size(max = 160) String idempotencyKey
) {
}
