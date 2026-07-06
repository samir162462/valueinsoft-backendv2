package com.example.valueinsoftbackend.Model.Request.InventoryReceipt;

/**
 * Party type that inventory is received from.
 * SUPPLIER is the default and preserves the historical purchasing flow.
 * CLIENT_TRADE_IN receives stock purchased from an existing client/customer.
 */
public enum AcquisitionSource {
    SUPPLIER,
    CLIENT_TRADE_IN;

    public static AcquisitionSource defaultIfNull(AcquisitionSource value) {
        return value == null ? SUPPLIER : value;
    }

    public boolean isClientTradeIn() {
        return this == CLIENT_TRADE_IN;
    }
}
