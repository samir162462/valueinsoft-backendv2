package com.example.valueinsoftbackend.Model.Request.InventoryReceipt;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductReceiptPublicCatalogRequest {
    private boolean showOnline;
    private boolean onlineActive;
    private String onlineDescription;
    private String onlineImageUrl;
    private BigDecimal onlineOfferPrice;
    private Integer onlineSortOrder;
}
