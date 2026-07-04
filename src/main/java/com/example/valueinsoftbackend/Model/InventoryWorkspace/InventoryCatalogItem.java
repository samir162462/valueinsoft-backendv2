package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCatalogItem {
    private Long productId;
    private String productName;
    private String barcode;
    private String serial;
    private String trackingType;
    private java.util.List<Long> productUnitIds;
    private java.util.List<String> unitIdentifiers;
    private java.util.List<Long> unitSupplierIds;
    private java.util.List<String> unitSupplierNames;
    private String businessLineKey;
    private String templateKey;
    private String groupKey;
    private String categoryKey;
    private String subcategoryKey;
    private String groupName;
    private String categoryName;
    private String subcategoryName;
    private String brand;
    private String model;
    private String manufacturer;
    private Integer taxonomyVersion;
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

    public InventoryCatalogItem(Long productId,
                                String productName,
                                String barcode,
                                String serial,
                                String trackingType,
                                List<Long> productUnitIds,
                                List<String> unitIdentifiers,
                                List<Long> unitSupplierIds,
                                List<String> unitSupplierNames,
                                String businessLineKey,
                                String templateKey,
                                Integer supplierId,
                                String supplierName,
                                Integer quantityOnHand,
                                String stockStatus,
                                Boolean lowStock,
                                Boolean sellable,
                                Boolean used,
                                Integer sellPrice,
                                Integer buyPrice,
                                String lastMovementAt,
                                String updatedAt) {
        this.productId = productId;
        this.productName = productName;
        this.barcode = barcode;
        this.serial = serial;
        this.trackingType = trackingType;
        this.productUnitIds = productUnitIds;
        this.unitIdentifiers = unitIdentifiers;
        this.unitSupplierIds = unitSupplierIds;
        this.unitSupplierNames = unitSupplierNames;
        this.businessLineKey = businessLineKey;
        this.templateKey = templateKey;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.quantityOnHand = quantityOnHand;
        this.stockStatus = stockStatus;
        this.lowStock = lowStock;
        this.sellable = sellable;
        this.used = used;
        this.sellPrice = sellPrice;
        this.buyPrice = buyPrice;
        this.lastMovementAt = lastMovementAt;
        this.updatedAt = updatedAt;
    }
}
