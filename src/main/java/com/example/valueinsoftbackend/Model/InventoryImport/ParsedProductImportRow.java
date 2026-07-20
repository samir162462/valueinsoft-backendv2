package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParsedProductImportRow {
    private final int rowNumber;
    private final Map<String, String> values;
    private final Map<String, String> computed = new LinkedHashMap<>();
    private final List<ProductImportErrorResponse> errors = new ArrayList<>();
    private ProductImportRowStatus status = ProductImportRowStatus.VALID;
    private Long existingProductId;
    private String action;

    public ParsedProductImportRow(int rowNumber, Map<String, String> values) {
        this.rowNumber = rowNumber;
        this.values = new LinkedHashMap<>(values);
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public Map<String, String> getValues() {
        return values;
    }

    /**
     * Derived values (tracking type, serialized identifier, group key, resolved
     * unit cost) computed during validation. They are persisted into
     * normalized_data so the confirm step can consume them without re-deriving.
     */
    public void putComputed(String key, String value) {
        computed.put(key, value == null ? "" : value);
    }

    public String computed(String key) {
        return computed.getOrDefault(key, "");
    }

    /**
     * Raw CSV values merged with computed values; stored as normalized_data.
     */
    public Map<String, String> getNormalizedValues() {
        Map<String, String> merged = new LinkedHashMap<>(values);
        merged.putAll(computed);
        return merged;
    }

    public List<ProductImportErrorResponse> getErrors() {
        return errors;
    }

    public ProductImportRowStatus getStatus() {
        return status;
    }

    public Long getExistingProductId() {
        return existingProductId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setExistingProductId(Long existingProductId) {
        this.existingProductId = existingProductId;
    }

    public String value(String header) {
        return values.getOrDefault(header, "");
    }

    public void addError(String fieldName, String code, String message, String rawValue) {
        errors.add(new ProductImportErrorResponse(fieldName, code, message, ProductImportErrorSeverity.ERROR.name(), rawValue));
        status = ProductImportRowStatus.INVALID;
    }

    public void addWarning(String fieldName, String code, String message, String rawValue) {
        errors.add(new ProductImportErrorResponse(fieldName, code, message, ProductImportErrorSeverity.WARNING.name(), rawValue));
        if (status == ProductImportRowStatus.VALID) {
            status = ProductImportRowStatus.WARNING;
        }
    }

    public void markDuplicate(String fieldName, String code, String message, String rawValue) {
        errors.add(new ProductImportErrorResponse(fieldName, code, message, ProductImportErrorSeverity.ERROR.name(), rawValue));
        status = ProductImportRowStatus.DUPLICATE;
    }
}
