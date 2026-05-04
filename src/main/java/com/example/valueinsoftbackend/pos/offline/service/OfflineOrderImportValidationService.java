package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.model.OfflineValidationProductSnapshot;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderValidationRepository;
import com.example.valueinsoftbackend.pos.offline.repository.PosDeviceRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service responsible for validating offline order imports.
 * It performs various checks including tenant access, device validity, cashier ownership,
 * idempotency, and detailed payload verification (totals, prices, quantities, products).
 */
@Service
public class OfflineOrderImportValidationService {

    /**
     * Tolerance used for comparing BigDecimal amounts (e.g., to handle minor rounding differences).
     */
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final OfflineOrderValidationRepository validationRepo;
    private final PosDeviceRepository deviceRepo;
    private final PosIdempotencyService idempotencyService;

    /**
     * Constructs a new OfflineOrderImportValidationService with required dependencies.
     *
     * @param validationRepo     the repository for validation-related database queries
     * @param deviceRepo         the repository for POS device management
     * @param idempotencyService the service for handling request idempotency
     */
    public OfflineOrderImportValidationService(OfflineOrderValidationRepository validationRepo,
                                               PosDeviceRepository deviceRepo,
                                               PosIdempotencyService idempotencyService) {
        this.validationRepo = validationRepo;
        this.deviceRepo = deviceRepo;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Validates an offline order import record.
     *
     * @param importRecord the import record to validate
     * @return a list of validation errors, if any
     */
    public List<ValidationError> validate(OfflineOrderImportModel importRecord) {
        List<ValidationError> errors = new ArrayList<>();
        Long companyId = importRecord.companyId();
        Long branchId = importRecord.branchId();

        // 1. Basic tenant and ownership checks
        if (!validationRepo.branchBelongsToCompany(companyId, branchId)) {
            errors.add(error("INVALID_TENANT_ACCESS", "Branch does not belong to company", "branchId"));
        }
        if (deviceRepo.findById(companyId, branchId, importRecord.deviceId()).isEmpty()) {
            errors.add(error("OFFLINE_DEVICE_NOT_FOUND", "Device does not belong to company/branch", "deviceId"));
        }
        if (importRecord.cashierId() != null && !validationRepo.cashierBelongsToBranch(importRecord.cashierId(), branchId)) {
            errors.add(error("OFFLINE_CASHIER_NOT_FOUND", "Cashier does not belong to branch", "cashierId"));
        }

        // 2. Idempotency check
        try {
            idempotencyService.requireMatchingRecord(
                    companyId,
                    branchId,
                    importRecord.deviceId(),
                    importRecord.idempotencyKey(),
                    importRecord.payloadHash());
        } catch (OfflineSyncException ex) {
            errors.add(error(ex.getErrorCode(), ex.getMessage(), "idempotencyKey"));
        }

        // 3. Payload structure and content validation
        JsonObject order = parsePayload(importRecord.payloadJson(), errors);
        if (order == null) {
            return errors;
        }

        validateRequiredString(order, "offlineOrderNo", "OFFLINE_ORDER_PAYLOAD_INVALID", errors);
        validateRequiredString(order, "idempotencyKey", "OFFLINE_ORDER_PAYLOAD_INVALID", errors);

        JsonArray items = array(order, "items");
        if (items == null || items.isEmpty()) {
            errors.add(error("OFFLINE_ORDER_EMPTY_LINES", "Order must contain at least one line", "items"));
            return errors;
        }

        // 4. Product resolution and line-level validation
        ProductLookups products = loadProducts(companyId, branchId, items);
        BigDecimal lineSubtotal = BigDecimal.ZERO;
        BigDecimal lineDiscount = BigDecimal.ZERO;
        BigDecimal lineTax = BigDecimal.ZERO;
        BigDecimal linePayable = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            JsonObject item = object(items.get(i));
            if (item == null) {
                errors.add(error("OFFLINE_ORDER_PAYLOAD_INVALID", "Line item must be an object", "items[" + i + "]"));
                continue;
            }

            OfflineValidationProductSnapshot product = resolveProduct(item, products);
            if (product == null) {
                errors.add(error("OFFLINE_PRODUCT_NOT_FOUND", "Product was not found", "items[" + i + "].productId"));
            } else if (!product.availableInBranch()) {
                errors.add(error("OFFLINE_PRODUCT_NOT_AVAILABLE_IN_BRANCH",
                        "Product is not available in this branch", "items[" + i + "].productId"));
            } else if (!product.active()) {
                errors.add(error("OFFLINE_PRODUCT_NOT_FOUND", "Product is not active for offline sale", "items[" + i + "].productId"));
            }

            BigDecimal quantity = decimal(item, "quantity");
            BigDecimal unitPrice = decimal(item, "unitPrice");
            BigDecimal discount = nonNull(decimal(item, "discountAmount"));
            BigDecimal tax = nonNull(decimal(item, "taxAmount"));
            BigDecimal lineTotal = decimal(item, "lineTotal");

            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(error("OFFLINE_INVALID_QUANTITY", "Quantity must be greater than zero", "items[" + i + "].quantity"));
            }
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(error("OFFLINE_INVALID_PRICE", "Unit price must be non-negative", "items[" + i + "].unitPrice"));
            } else if (product != null && product.lowestPrice() != null
                    && unitPrice.compareTo(product.lowestPrice()) < 0) {
                errors.add(error("OFFLINE_INVALID_PRICE", "Unit price is below the configured lowest price", "items[" + i + "].unitPrice"));
            }
            if (lineTotal == null || lineTotal.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(error("OFFLINE_TOTAL_MISMATCH", "Line total must be non-negative", "items[" + i + "].lineTotal"));
            }

            if (quantity != null && unitPrice != null && lineTotal != null) {
                BigDecimal expectedLineTotal = quantity.multiply(unitPrice).subtract(discount).add(tax);
                if (!sameAmount(expectedLineTotal, lineTotal)) {
                    errors.add(error("OFFLINE_TOTAL_MISMATCH",
                            "Line total does not match quantity, price, discount, and tax", "items[" + i + "].lineTotal"));
                }
                lineSubtotal = lineSubtotal.add(quantity.multiply(unitPrice));
                lineDiscount = lineDiscount.add(discount);
                lineTax = lineTax.add(tax);
                linePayable = linePayable.add(lineTotal);
            }
        }

        // 5. Header-level total consistency
        validateOrderTotals(order, lineSubtotal, lineDiscount, lineTax, linePayable, errors);
        
        // 6. Payment consistency
        validatePayments(order, linePayable, errors);
        
        return errors;
    }

    /**
     * Loads product details from the database for all products referenced in the order items.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param items     the list of order items
     * @return a lookup object containing product snapshots by ID and barcode
     */
    private ProductLookups loadProducts(Long companyId, Long branchId, JsonArray items) {
        Set<Long> productIds = new HashSet<>();
        Set<String> barcodes = new HashSet<>();
        for (JsonElement element : items) {
            JsonObject item = object(element);
            if (item == null) {
                continue;
            }
            Long productId = longValue(item, "productId");
            String barcode = stringValue(item, "barcode");
            if (productId != null && productId > 0) {
                productIds.add(productId);
            }
            if (barcode != null && !barcode.isBlank()) {
                barcodes.add(barcode);
            }
        }

        Map<Long, OfflineValidationProductSnapshot> byId = new HashMap<>();
        Map<String, OfflineValidationProductSnapshot> byBarcode = new HashMap<>();
        for (OfflineValidationProductSnapshot product :
                validationRepo.findProductsForValidation(companyId, branchId, productIds, barcodes)) {
            byId.put(product.productId(), product);
            if (product.barcode() != null) {
                byBarcode.put(product.barcode(), product);
            }
        }
        return new ProductLookups(byId, byBarcode);
    }

    /**
     * Resolves a product for an item using either productId or barcode.
     *
     * @param item     the order item
     * @param products the loaded product lookups
     * @return the resolved product snapshot, or null if not found
     */
    private OfflineValidationProductSnapshot resolveProduct(JsonObject item, ProductLookups products) {
        Long productId = longValue(item, "productId");
        if (productId != null && products.byId().containsKey(productId)) {
            return products.byId().get(productId);
        }
        String barcode = stringValue(item, "barcode");
        if (barcode != null) {
            return products.byBarcode().get(barcode);
        }
        return null;
    }

    /**
     * Validates that the submitted header totals match the sum of line items.
     *
     * @param order    the order payload
     * @param subtotal the calculated subtotal from lines
     * @param discount the calculated discount from lines
     * @param tax      the calculated tax from lines
     * @param payable  the calculated payable amount from lines
     * @param errors   the list to add errors to
     */
    private void validateOrderTotals(JsonObject order, BigDecimal subtotal, BigDecimal discount,
                                     BigDecimal tax, BigDecimal payable, List<ValidationError> errors) {
        BigDecimal submittedSubtotal = decimal(order, "subtotalAmount");
        BigDecimal submittedDiscount = nonNull(decimal(order, "discountAmount"));
        BigDecimal submittedTax = nonNull(decimal(order, "taxAmount"));
        BigDecimal submittedTotal = decimal(order, "totalAmount");

        if (submittedSubtotal == null || submittedTotal == null) {
            errors.add(error("OFFLINE_ORDER_PAYLOAD_INVALID", "Order subtotalAmount and totalAmount are required", "totalAmount"));
            return;
        }
        if (!sameAmount(subtotal, submittedSubtotal)
                || !sameAmount(discount, submittedDiscount)
                || !sameAmount(tax, submittedTax)
                || !sameAmount(payable, submittedTotal)) {
            errors.add(error("OFFLINE_TOTAL_MISMATCH",
                    "Order totals do not match line totals, discounts, and taxes", "totalAmount"));
        }
    }

    /**
     * Validates that the sum of payments matches the payable amount of the order.
     *
     * @param order   the order payload
     * @param payable the expected total payable amount
     * @param errors  the list to add errors to
     */
    private void validatePayments(JsonObject order, BigDecimal payable, List<ValidationError> errors) {
        JsonArray payments = array(order, "payments");
        if (payments == null || payments.isEmpty()) {
            return;
        }
        BigDecimal paymentTotal = BigDecimal.ZERO;
        for (int i = 0; i < payments.size(); i++) {
            JsonObject payment = object(payments.get(i));
            if (payment == null) {
                errors.add(error("OFFLINE_ORDER_PAYLOAD_INVALID", "Payment must be an object", "payments[" + i + "]"));
                continue;
            }
            BigDecimal amount = decimal(payment, "amount");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(error("OFFLINE_PAYMENT_TOTAL_MISMATCH", "Payment amount must be non-negative", "payments[" + i + "].amount"));
            } else {
                paymentTotal = paymentTotal.add(amount);
            }
        }
        if (!sameAmount(paymentTotal, payable)) {
            errors.add(error("OFFLINE_PAYMENT_TOTAL_MISMATCH",
                    "Payment total does not match payable total", "payments"));
        }
    }

    /**
     * Parses the raw JSON payload and handles parsing errors.
     *
     * @param payloadJson the raw JSON string
     * @param errors      the list to add errors to
     * @return the parsed JsonObject, or null if parsing fails
     */
    private JsonObject parsePayload(String payloadJson, List<ValidationError> errors) {
        try {
            JsonElement element = JsonParser.parseString(payloadJson);
            if (!element.isJsonObject()) {
                errors.add(error("OFFLINE_ORDER_PAYLOAD_INVALID", "Order payload must be a JSON object", "payload"));
                return null;
            }
            return element.getAsJsonObject();
        } catch (Exception ex) {
            errors.add(error("OFFLINE_ORDER_PAYLOAD_INVALID", "Order payload is not readable JSON", "payload"));
            return null;
        }
    }

    /**
     * Validates that a required string field exists and is not blank.
     *
     * @param object    the JSON object to check
     * @param field     the field name
     * @param errorCode the error code to use if missing
     * @param errors    the list to add errors to
     */
    private void validateRequiredString(JsonObject object, String field, String errorCode, List<ValidationError> errors) {
        String value = stringValue(object, field);
        if (value == null || value.isBlank()) {
            errors.add(error(errorCode, field + " is required", field));
        }
    }

    /**
     * Compares two amounts for equality within a small tolerance.
     *
     * @param expected the expected amount
     * @param actual   the actual amount
     * @return true if the amounts are essentially equal
     */
    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        return expected.subtract(actual).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

    /**
     * Safely extracts a BigDecimal from a JSON object.
     *
     * @param object the JSON object
     * @param field  the field name
     * @return the BigDecimal value, or null if missing/invalid
     */
    private BigDecimal decimal(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Ensures a BigDecimal is not null by returning zero if it is.
     *
     * @param value the value to check
     * @return the value, or BigDecimal.ZERO if null
     */
    private BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Safely extracts a Long from a JSON object.
     *
     * @param object the JSON object
     * @param field  the field name
     * @return the Long value, or null if missing/invalid
     */
    private Long longValue(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsLong();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Safely extracts a String from a JSON object.
     *
     * @param object the JSON object
     * @param field  the field name
     * @return the String value, or null if missing/invalid
     */
    private String stringValue(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Safely extracts a JsonArray from a JSON object.
     *
     * @param object the JSON object
     * @param field  the field name
     * @return the JsonArray, or null if missing/invalid
     */
    private JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    /**
     * Safely extracts a JsonObject from a JsonElement.
     *
     * @param element the JSON element
     * @return the JsonObject, or null if missing/invalid
     */
    private JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /**
     * Helper to create a new ValidationError.
     *
     * @param code      the error code
     * @param message   the error message
     * @param fieldPath the path to the field causing the error
     * @return a new ValidationError object
     */
    private ValidationError error(String code, String message, String fieldPath) {
        return new ValidationError(code, message, fieldPath);
    }

    /**
     * Internal record for batch-loading products for validation.
     */
    private record ProductLookups(Map<Long, OfflineValidationProductSnapshot> byId,
                                  Map<String, OfflineValidationProductSnapshot> byBarcode) {
    }

    /**
     * Represents a single validation error found during order import.
     */
    public record ValidationError(String code, String message, String fieldPath) {
    }
}
