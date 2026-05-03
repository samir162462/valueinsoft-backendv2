package com.example.valueinsoftbackend.pos.offline.enums;

/**
 * Standardized error codes for the offline sync pipeline.
 * Each code maps to a specific validation or processing failure
 * that can occur during offline order import.
 */
public enum OfflineSyncErrorCode {

    // Device validation
    DEVICE_NOT_REGISTERED,
    DEVICE_BLOCKED,
    DEVICE_OFFLINE_NOT_ALLOWED,
    OFFLINE_WINDOW_EXCEEDED,

    // Branch / cashier validation
    INVALID_BRANCH,
    INVALID_CASHIER,

    // Idempotency
    DUPLICATE_IDEMPOTENCY_KEY,
    IDEMPOTENCY_PAYLOAD_MISMATCH,

    // Product validation
    PRODUCT_NOT_FOUND,
    PRODUCT_INACTIVE,
    PRICE_CHANGED,

    // Discount / tax
    INVALID_DISCOUNT,
    INVALID_TAX_AMOUNT,

    // Payment
    PAYMENT_TOTAL_MISMATCH,

    // Inventory
    STOCK_UNAVAILABLE,

    // Shift
    SHIFT_CLOSED,

    // Posting
    FINANCE_POSTING_FAILED,
    INVENTORY_POSTING_FAILED,

    // System
    INTERNAL_PROCESSING_ERROR
}
