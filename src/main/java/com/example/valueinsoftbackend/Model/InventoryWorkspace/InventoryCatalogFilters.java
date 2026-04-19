package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCatalogFilters {
    private ArrayList<String> stockState = new ArrayList<>();
    private ArrayList<String> itemState = new ArrayList<>();
    private String businessLineKey;
    private String templateKey;
    private Integer supplierId;
    private String major;
    private Integer quantityMin;
    private Integer quantityMax;
    private Integer buyPriceMin;
    private Integer buyPriceMax;
    private Integer sellPriceMin;
    private Integer sellPriceMax;
    private Boolean hasBarcode;
    private Boolean hasSupplier;
    private String createdFrom;
    private String createdTo;
    private String lastMovementFrom;
    private String lastMovementTo;
}
