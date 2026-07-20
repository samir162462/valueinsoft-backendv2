package com.example.valueinsoftbackend.Model.InventoryImport;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Optional batch-level payment settings for confirming a product import.
 * Applies to serialized (IMEI/SERIAL) groups, which are received through the
 * product receipt pipeline. v1 supports FULL (pay everything now) and LATER
 * (all on credit, AP open items per supplier). Defaults to LATER.
 */
@Data
public class ProductImportConfirmRequest {

    @Size(max = 10)
    private String paymentOption;

    @Size(max = 30)
    private String paymentMethod;
}
