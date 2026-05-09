package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryImport.ProductImportRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryImport.ParsedProductImportRow;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportBatchSummaryResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportFileDownloadResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportHistoryResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportMode;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportRowStatus;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportRowsPageResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportValidateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductImportValidationService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportValidationService.class);

    private final ProductImportCsvParserService parserService;
    private final ProductImportRepository importRepository;
    private final ProductImportAuditService auditService;
    private final ProductImportFileStorageService fileStorageService;
    private final ProductImportErrorReportService errorReportService;

    public ProductImportValidationService(ProductImportCsvParserService parserService,
                                          ProductImportRepository importRepository,
                                          ProductImportAuditService auditService,
                                          ProductImportFileStorageService fileStorageService,
                                          ProductImportErrorReportService errorReportService) {
        this.parserService = parserService;
        this.importRepository = importRepository;
        this.auditService = auditService;
        this.fileStorageService = fileStorageService;
        this.errorReportService = errorReportService;
    }

    @Transactional
    public ProductImportValidateResponse validate(String principalName,
                                                  int companyId,
                                                  int branchId,
                                                  ProductImportMode mode,
                                                  boolean createMissingCategories,
                                                  boolean createMissingSuppliers,
                                                  boolean allowSellingBelowPurchase,
                                                  MultipartFile file) {
        long startedAt = System.nanoTime();
        if (createMissingCategories || createMissingSuppliers) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_CREATE_LOOKUPS_NOT_IMPLEMENTED",
                    "Creating missing categories or suppliers is not implemented yet");
        }

        List<ParsedProductImportRow> rows = parserService.parse(file);
        Long batchId = importRepository.createBatch(
                companyId,
                branchId,
                mode,
                file.getOriginalFilename(),
                fileHash(file),
                principalName,
                settings(mode, createMissingCategories, createMissingSuppliers, allowSellingBelowPurchase));
        auditService.logEvent(
                companyId,
                branchId,
                batchId,
                "PRODUCT_IMPORT_VALIDATION_STARTED",
                "Product import CSV validation started",
                principalName,
                Map.of(
                        "mode", mode.name(),
                        "fileName", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                        "fileSize", file.getSize()));
        fileStorageService.storeOriginalCsv(companyId, branchId, batchId, file);

        validateRows(companyId, branchId, mode, allowSellingBelowPurchase, rows);
        importRepository.insertRows(companyId, branchId, batchId, rows);

        int validRows = count(rows, ProductImportRowStatus.VALID);
        int warningRows = count(rows, ProductImportRowStatus.WARNING);
        int invalidRows = count(rows, ProductImportRowStatus.INVALID);
        int duplicateRows = count(rows, ProductImportRowStatus.DUPLICATE);
        importRepository.finalizeBatch(companyId, batchId, rows.size(), validRows, warningRows, invalidRows, duplicateRows);
        fileStorageService.storeErrorReportCsv(
                companyId,
                branchId,
                batchId,
                errorReportService.buildErrorReportCsv(companyId, branchId, batchId));

        List<String> warnings = new ArrayList<>();
        if (warningRows > 0) {
            warnings.add(warningRows + " rows have warnings and require review before confirmation.");
        }
        if (duplicateRows > 0) {
            warnings.add(duplicateRows + " rows are duplicates and are not eligible for import.");
        }
        auditService.logEvent(
                companyId,
                branchId,
                batchId,
                "PRODUCT_IMPORT_VALIDATED",
                "Product import CSV validation completed",
                principalName,
                Map.of(
                        "mode", mode.name(),
                        "totalRows", rows.size(),
                        "validRows", validRows,
                        "warningRows", warningRows,
                        "invalidRows", invalidRows,
                        "duplicateRows", duplicateRows));

        log.info("Product import validation completed: companyId={}, branchId={}, batchId={}, rows={}, valid={}, warning={}, invalid={}, duplicate={}, durationMs={}",
                companyId,
                branchId,
                batchId,
                rows.size(),
                validRows,
                warningRows,
                invalidRows,
                duplicateRows,
                (System.nanoTime() - startedAt) / 1_000_000L);

        return new ProductImportValidateResponse(
                batchId,
                invalidRows > 0 || duplicateRows > 0 ? "VALIDATED_WITH_ERRORS" : "VALIDATED",
                mode.name(),
                rows.size(),
                validRows,
                warningRows,
                invalidRows,
                duplicateRows,
                validRows + warningRows > 0,
                warnings);
    }

    public ProductImportRowsPageResponse rows(int companyId, long batchId, String status, int page, int size) {
        return importRepository.findRows(companyId, batchId, status, page, size);
    }

    public ProductImportBatchSummaryResponse summary(int companyId, long batchId) {
        ProductImportBatchSummaryResponse summary = importRepository.findBatchSummary(companyId, batchId);
        if (summary == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "Import batch was not found");
        }
        return summary;
    }

    public ProductImportHistoryResponse history(int companyId, int branchId, int page, int size) {
        return importRepository.findHistory(companyId, branchId, page, size);
    }

    public ProductImportFileDownloadResponse fileDownloadUrl(int companyId, int branchId, long batchId, String fileType) {
        return fileStorageService.downloadUrl(companyId, branchId, batchId, fileType);
    }

    private void validateRows(int companyId, int branchId, ProductImportMode mode, boolean allowSellingBelowPurchase, List<ParsedProductImportRow> rows) {
        Set<String> categories = importRepository.existingCategories(companyId, branchId);
        Set<String> units = importRepository.existingUnits(companyId);
        Set<String> suppliers = importRepository.existingSuppliers(companyId, branchId);

        Set<String> barcodes = nonBlankValues(rows, "barcode");
        Map<String, Long> existingByBarcode = importRepository.existingProductsByBarcode(companyId, barcodes);
        markFileDuplicates(rows, "sku", "DUPLICATE_SKU_IN_FILE", "SKU is duplicated inside the CSV file");
        markFileDuplicates(rows, "barcode", "DUPLICATE_BARCODE_IN_FILE", "Barcode is duplicated inside the CSV file");

        for (ParsedProductImportRow row : rows) {
            validateText(row, "product_name", true, 30);
            validateText(row, "barcode", true, 35);
            validateText(row, "category", true, 30);
            validateText(row, "subcategory", false, 15);
            validateText(row, "description", false, 60);
            validateText(row, "business_line_key", false, 40);
            validateText(row, "template_key", false, 80);
            validateText(row, "pricing_policy_code", false, 40);
            validateBranch(row, branchId);
            validateLookup(row, "category", categories, "CATEGORY_NOT_FOUND", "Category does not exist");
            validateLookup(row, "unit_code", units, "UNIT_NOT_FOUND", "Unit code does not exist");
            validateSupplier(row, suppliers);
            validatePrices(row, allowSellingBelowPurchase);
            validateIntegerQuantity(row, "opening_stock_quantity", true);
            validateIntegerQuantity(row, "minimum_stock_quantity", false);
            validateBoolean(row, "active", false);
            validateBoolean(row, "serial_required", false);
            validateBoolean(row, "allow_below_cost", false);
            validateEnum(row, "product_state", Set.of("NEW", "USED"), "PRODUCT_STATE_INVALID", "Product state must be New or Used");
            validateWarranty(row);
            validateUrl(row);
            validateDate(row);

            Long existingProductId = existingByBarcode.get(normalize(row.value("barcode")));
            if (existingProductId != null) {
                row.setExistingProductId(existingProductId);
            }
            assignAction(row, mode, existingProductId);
        }
    }

    private void assignAction(ParsedProductImportRow row, ProductImportMode mode, Long existingProductId) {
        if (row.getStatus() == ProductImportRowStatus.INVALID || row.getStatus() == ProductImportRowStatus.DUPLICATE) {
            row.setAction("SKIP");
            return;
        }

        switch (mode) {
            case ADD_ONLY -> {
                if (existingProductId != null) {
                    row.addError("barcode", "PRODUCT_ALREADY_EXISTS", "Barcode already exists in inventory_product.serial", row.value("barcode"));
                    row.setAction("SKIP");
                } else {
                    row.setAction("INSERT");
                }
            }
            case UPDATE_ONLY -> {
                if (existingProductId == null) {
                    row.addError("barcode", "PRODUCT_NOT_FOUND_FOR_UPDATE", "Barcode does not match an existing product for update", row.value("barcode"));
                    row.setAction("SKIP");
                } else {
                    row.setAction("UPDATE");
                }
            }
            case UPSERT -> row.setAction(existingProductId == null ? "INSERT" : "UPDATE");
        }
    }

    private void validateText(ParsedProductImportRow row, String field, boolean required, int maxLength) {
        String value = row.value(field);
        if (required && value.isBlank()) {
            row.addError(field, field.toUpperCase(Locale.ROOT) + "_REQUIRED", field + " is required", value);
            return;
        }
        if (!value.isBlank() && value.length() > maxLength) {
            row.addError(field, field.toUpperCase(Locale.ROOT) + "_TOO_LONG", field + " must be " + maxLength + " characters or fewer", value);
        }
    }

    private void validateBranch(ParsedProductImportRow row, int branchId) {
        String value = row.value("branch_id");
        if (value.isBlank()) {
            row.addError("branch_id", "BRANCH_ID_REQUIRED", "branch_id is required", value);
            return;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed != branchId) {
                row.addError("branch_id", "BRANCH_MISMATCH", "CSV branch_id must match the selected branch", value);
            }
        } catch (NumberFormatException ex) {
            row.addError("branch_id", "BRANCH_ID_INVALID", "branch_id must be numeric", value);
        }
    }

    private void validateLookup(ParsedProductImportRow row, String field, Set<String> lookup, String code, String message) {
        String value = row.value(field);
        if (!value.isBlank() && !lookup.contains(normalize(value))) {
            row.addError(field, code, message, value);
        }
    }

    private void validateSupplier(ParsedProductImportRow row, Set<String> suppliers) {
        String supplierName = row.value("supplier_name");
        if (!supplierName.isBlank() && !suppliers.contains(normalize(supplierName))) {
            row.addError("supplier_name", "SUPPLIER_NOT_FOUND", "Supplier does not exist", supplierName);
        }
    }

    private void validatePrices(ParsedProductImportRow row, boolean allowSellingBelowPurchase) {
        BigDecimal purchase = parseMoney(row, "purchase_price", true);
        BigDecimal selling = parseMoney(row, "selling_price", true);
        BigDecimal wholesale = parseMoney(row, "wholesale_price", false);

        if (purchase != null && selling != null && selling.compareTo(purchase) < 0 && !allowSellingBelowPurchase) {
            row.addError("selling_price", "SELLING_BELOW_PURCHASE", "Selling price cannot be less than purchase price", row.value("selling_price"));
        }
        if (wholesale != null && purchase != null && wholesale.compareTo(purchase) < 0) {
            row.addError("wholesale_price", "WHOLESALE_BELOW_PURCHASE", "Wholesale price cannot be less than purchase price", row.value("wholesale_price"));
        }
        if (wholesale != null && selling != null && wholesale.compareTo(selling) > 0) {
            row.addError("wholesale_price", "WHOLESALE_ABOVE_SELLING", "Wholesale price cannot be greater than selling price", row.value("wholesale_price"));
        }
    }

    private BigDecimal parseMoney(ParsedProductImportRow row, String field, boolean required) {
        String value = row.value(field);
        if (value.isBlank()) {
            if (required) {
                row.addError(field, field.toUpperCase(Locale.ROOT) + "_REQUIRED", field + " is required", value);
            }
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                row.addError(field, "PRICE_NEGATIVE", field + " cannot be negative", value);
                return null;
            }
            if (parsed.stripTrailingZeros().scale() > 0) {
                row.addError(field, "PRICE_DECIMAL_NOT_SUPPORTED", field + " must be integer-compatible until product price columns support decimals", value);
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            row.addError(field, "PRICE_INVALID", field + " must be numeric", value);
            return null;
        }
    }

    private void validateIntegerQuantity(ParsedProductImportRow row, String field, boolean required) {
        String value = row.value(field);
        if (value.isBlank()) {
            if (required) {
                row.addError(field, field.toUpperCase(Locale.ROOT) + "_REQUIRED", field + " is required", value);
            }
            return;
        }
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.ZERO) < 0 || parsed.stripTrailingZeros().scale() > 0) {
                row.addError(field, "QUANTITY_INVALID", field + " must be a non-negative integer", value);
            }
        } catch (NumberFormatException ex) {
            row.addError(field, "QUANTITY_INVALID", field + " must be numeric", value);
        }
    }

    private void validateBoolean(ParsedProductImportRow row, String field, boolean required) {
        String value = row.value(field);
        if (value.isBlank()) {
            if (required) {
                row.addError(field, field.toUpperCase(Locale.ROOT) + "_REQUIRED", field + " is required", value);
            }
            return;
        }
        String normalized = normalize(value);
        if (!Set.of("TRUE", "FALSE", "YES", "NO", "1", "0").contains(normalized)) {
            row.addError(field, "BOOLEAN_INVALID", field + " must be true/false, yes/no, or 1/0", value);
        }
    }

    private void validateEnum(ParsedProductImportRow row, String field, Set<String> allowed, String code, String message) {
        String value = row.value(field);
        if (!value.isBlank() && !allowed.contains(normalize(value))) {
            row.addError(field, code, message, value);
        }
    }

    private void validateWarranty(ParsedProductImportRow row) {
        String value = row.value("warranty_period_days");
        if (value.isBlank()) {
            return;
        }
        try {
            if (Integer.parseInt(value) < 0) {
                row.addError("warranty_period_days", "WARRANTY_PERIOD_INVALID", "Warranty period must be non-negative", value);
            }
        } catch (NumberFormatException ex) {
            row.addError("warranty_period_days", "WARRANTY_PERIOD_INVALID", "Warranty period must be numeric", value);
        }
    }

    private void validateUrl(ParsedProductImportRow row) {
        String value = row.value("image_url");
        if (!value.isBlank() && !(value.startsWith("http://") || value.startsWith("https://"))) {
            row.addError("image_url", "IMAGE_URL_INVALID", "Image URL must start with http:// or https://", value);
        }
    }

    private void validateDate(ParsedProductImportRow row) {
        String value = row.value("expiry_date");
        if (!value.isBlank() && !value.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            row.addError("expiry_date", "EXPIRY_DATE_INVALID", "Expiry date must use YYYY-MM-DD", value);
        }
    }

    private void markFileDuplicates(List<ParsedProductImportRow> rows, String field, String code, String message) {
        Map<String, Integer> counts = new HashMap<>();
        for (ParsedProductImportRow row : rows) {
            String value = normalize(row.value(field));
            if (!value.isBlank()) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        for (ParsedProductImportRow row : rows) {
            String value = normalize(row.value(field));
            if (!value.isBlank() && counts.getOrDefault(value, 0) > 1) {
                row.markDuplicate(field, code, message, row.value(field));
            }
        }
    }

    private Set<String> nonBlankValues(List<ParsedProductImportRow> rows, String field) {
        Set<String> values = new HashSet<>();
        for (ParsedProductImportRow row : rows) {
            String value = normalize(row.value(field));
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private int count(List<ParsedProductImportRow> rows, ProductImportRowStatus status) {
        int count = 0;
        for (ParsedProductImportRow row : rows) {
            if (row.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Object> settings(ProductImportMode mode, boolean createMissingCategories,
                                         boolean createMissingSuppliers, boolean allowSellingBelowPurchase) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("mode", mode.name());
        settings.put("createMissingCategories", createMissingCategories);
        settings.put("createMissingSuppliers", createMissingSuppliers);
        settings.put("allowSellingBelowPurchase", allowSellingBelowPurchase);
        return settings;
    }

    private String fileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                byte[] buffer = new byte[8192];
                while (digestInputStream.read(buffer) != -1) {
                    // DigestInputStream updates the digest as bytes are consumed.
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_HASH_FAILED", "Unable to hash CSV file");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
