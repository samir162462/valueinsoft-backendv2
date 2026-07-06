package com.example.valueinsoftbackend.Model.Request.InventoryReceipt;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProductReceiptDetailsRequest {

    /**
     * Where the stock comes from. Defaults to SUPPLIER so existing callers are
     * untouched. When CLIENT_TRADE_IN is selected, clientId is required and
     * supplierId must be omitted.
     */
    private AcquisitionSource acquisitionSource = AcquisitionSource.SUPPLIER;

    /**
     * Required when acquisitionSource == SUPPLIER. Conditional validation is
     * enforced in InventoryProductReceiptService (0 = absent).
     */
    private int supplierId;

    /**
     * Required when acquisitionSource == CLIENT_TRADE_IN: the existing, active
     * client selling the product to the shop.
     */
    @Positive(message = "clientId must be positive when provided")
    private Integer clientId;

    /**
     * Mandatory received-stock condition. Defaults to NEW. For serialized
     * products the value is stamped on each received unit; for non-serialized
     * products it lives on the receipt ledger line.
     */
    @Size(max = 10)
    private String conditionCode;

    @Size(max = 255)
    private String conditionNotes;

    /**
     * FULL | PARTIAL | LATER. Optional for backward compatibility; when absent
     * it is derived from paidAmount vs. total.
     */
    @Size(max = 10)
    private String paymentOption;

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
