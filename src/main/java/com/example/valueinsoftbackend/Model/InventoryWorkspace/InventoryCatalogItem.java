package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCatalogItem {
    private Long productId;
    private String productName;
    private String barcode;
    private String serial;
    private String businessLineKey;
    private String templateKey;
    private Integer supplierId;
    private String supplierName;
    private Integer quantityOnHand;
    private String stockStatus;
    private Boolean lowStock;
    private Boolean sellable;
    private Boolean used;
    private Integer sellPrice;
    private Integer buyPrice;
    private String lastMovementAt;
    private String updatedAt;
}
