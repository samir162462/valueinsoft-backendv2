/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * One buying-price (purchase / stock-in) transaction of a product,
 * shown in the product details dialog with its date and time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPurchaseHistoryResponse {
    private long entryId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String payType;
    private long supplierId;
    private String supplierName;
    private String userName;
    private String serial;
    private Timestamp time;
}
