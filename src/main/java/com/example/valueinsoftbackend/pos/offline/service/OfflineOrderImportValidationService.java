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

@Service
public class OfflineOrderImportValidationService {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final OfflineOrderValidationRepository validationRepo;
    private final PosDeviceRepository deviceRepo;
    private final PosIdempotencyService idempotencyService;

    public OfflineOrderImportValidationService(OfflineOrderValidationRepository validationRepo,
                                               PosDeviceRepository deviceRepo,
                                               PosIdempotencyService idempotencyService) {
        this.validationRepo = validationRepo;
        this.deviceRepo = deviceRepo;
        this.idempotencyService = idempotencyService;
    }

    public List<ValidationError> validate(OfflineOrderImportModel importRecord) {
        List<ValidationError> errors = new ArrayList<>();
        Long companyId = importRecord.companyId();
        Long branchId = importRecord.branchId();

        if (!validationRepo.branchBelongsToCompany(companyId, branchId)) {
            errors.add(error("INVALID_TENANT_ACCESS", "Branch does not belong to company", "branchId"));
        }
        if (deviceRepo.findById(companyId, branchId, importRecord.deviceId()).isEmpty()) {
            errors.add(error("OFFLINE_DEVICE_NOT_FOUND", "Device does not belong to company/branch", "deviceId"));
        }
        if (importRecord.cashierId() != null && !validationRepo.cashierBelongsToBranch(importRecord.cashierId(), branchId)) {
            errors.add(error("OFFLINE_CASHIER_NOT_FOUND", "Cashier does not belong to branch", "cashierId"));
        }

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

        validateOrderTotals(order, lineSubtotal, lineDiscount, lineTax, linePayable, errors);
        validatePayments(order, linePayable, errors);
        return errors;
    }

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

    private void validateRequiredString(JsonObject object, String field, String errorCode, List<ValidationError> errors) {
        String value = stringValue(object, field);
        if (value == null || value.isBlank()) {
            errors.add(error(errorCode, field + " is required", field));
        }
    }

    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        return expected.subtract(actual).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

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

    private BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

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

    private JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private ValidationError error(String code, String message, String fieldPath) {
        return new ValidationError(code, message, fieldPath);
    }

    private record ProductLookups(Map<Long, OfflineValidationProductSnapshot> byId,
                                  Map<String, OfflineValidationProductSnapshot> byBarcode) {
    }

    public record ValidationError(String code, String message, String fieldPath) {
    }
}
