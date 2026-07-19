package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InventorySupplierReturnRequest(
        @Positive int companyId,
        @Positive int branchId,
        @Positive long productId,
        @Positive int quantity,
        @NotNull @PositiveOrZero Long expectedVersion,
        @PositiveOrZero Integer refundAmount,
        @Size(max = 120) String note,
        @NotBlank @Size(max = 160) String idempotencyKey
) {
}
