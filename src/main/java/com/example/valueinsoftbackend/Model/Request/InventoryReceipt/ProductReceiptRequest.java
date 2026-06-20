package com.example.valueinsoftbackend.Model.Request.InventoryReceipt;

import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductReceiptRequest {
    @Positive(message = "companyId is required")
    private int companyId;

    @Positive(message = "branchId is required")
    private int branchId;

    @NotNull(message = "operationMode is required")
    private ProductReceiptOperationMode operationMode;

    private Long existingProductId;

    @Valid
    private ProductReceiptProductRequest product;

    @Valid
    @NotNull(message = "receipt is required")
    private ProductReceiptDetailsRequest receipt;

    @Valid
    private List<SerializedUnitInput> serializedUnits = new ArrayList<>();

    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 160, message = "idempotencyKey must be 160 characters or fewer")
    private String idempotencyKey;
}
