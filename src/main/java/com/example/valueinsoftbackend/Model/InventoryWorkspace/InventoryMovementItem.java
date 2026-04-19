package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementItem {
    private Long movementId;
    private String movementType;
    private String movementAt;
    private Long productId;
    private String productName;
    private String barcode;
    private String serial;
    private String templateKey;
    private String businessLineKey;
    private Integer supplierId;
    private String supplierName;
    private Integer quantityDelta;
    private Integer runningBalance;
    private String referenceType;
    private String referenceId;
    private String actorName;
}
