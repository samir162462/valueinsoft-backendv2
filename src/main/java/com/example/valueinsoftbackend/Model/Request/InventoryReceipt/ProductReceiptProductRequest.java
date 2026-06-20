package com.example.valueinsoftbackend.Model.Request.InventoryReceipt;

import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ProductReceiptProductRequest {
    @NotBlank(message = "productName is required")
    @Size(max = 30, message = "productName must be 30 characters or fewer")
    private String productName;

    @Size(max = 100)
    private String sku;

    @Size(max = 100)
    private String barcode;

    private Long categoryId;
    private Long subcategoryId;

    @Size(max = 30)
    private String categoryName;

    @Size(max = 15)
    private String subcategoryName;

    @Size(max = 40)
    private String businessLineKey;

    @Size(max = 80)
    private String templateKey;

    @Size(max = 20)
    private String baseUomCode;

    @Size(max = 40)
    private String pricingPolicyCode;

    @JsonAlias({"buyingPrice", "bPrice"})
    @PositiveOrZero(message = "buyingPrice must be zero or greater")
    private BigDecimal buyingPrice;

    @JsonAlias({"lowestPrice", "lPrice"})
    @PositiveOrZero(message = "lowestPrice must be zero or greater")
    private BigDecimal lowestPrice;

    @JsonAlias({"retailPrice", "rPrice"})
    @PositiveOrZero(message = "retailPrice must be zero or greater")
    private BigDecimal retailPrice;

    private TrackingType trackingType = TrackingType.QUANTITY;

    private ProductReceiptPublicCatalogRequest publicCatalog;

    private Map<String, Object> attributes = new LinkedHashMap<>();
}
