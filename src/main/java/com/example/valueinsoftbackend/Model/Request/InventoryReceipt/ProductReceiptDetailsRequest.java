package com.example.valueinsoftbackend.Model.Request.InventoryReceipt;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProductReceiptDetailsRequest {
    @Positive(message = "supplierId is required")
    private int supplierId;

    @Positive(message = "quantity must be greater than zero")
    private int quantity;

    @PositiveOrZero(message = "unitCost must be zero or greater")
    private BigDecimal unitCost;

    @Size(max = 30)
    private String paymentMethod;

    @PositiveOrZero(message = "paidAmount must be zero or greater")
    private BigDecimal paidAmount;

    private LocalDate receiptDate;

    @Size(max = 80)
    private String referenceNumber;

    @Size(max = 255)
    private String notes;
}
