package com.example.valueinsoftbackend.Model.Request.InventoryReceipt;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductReceiptClassificationRequest {
    @Size(max = 80, message = "groupKey must be 80 characters or fewer")
    private String groupKey;

    @Size(max = 80, message = "categoryKey must be 80 characters or fewer")
    private String categoryKey;

    @Size(max = 80, message = "subcategoryKey must be 80 characters or fewer")
    private String subcategoryKey;
}
