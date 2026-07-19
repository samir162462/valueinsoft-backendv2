package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProductCommandRepository;
import com.example.valueinsoftbackend.DatabaseRequests.InventoryImport.ProductImportRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportConfirmResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportStagedRow;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProductImportConfirmService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportConfirmService.class);

    private final ProductImportRepository importRepository;
    private final DbPosProductCommandRepository productCommandRepository;
    private final AuthorizationService authorizationService;
    private final ProductImportAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public ProductImportConfirmService(ProductImportRepository importRepository,
                                       DbPosProductCommandRepository productCommandRepository,
                                       AuthorizationService authorizationService,
                                       ProductImportAuditService auditService,
                                       PlatformTransactionManager transactionManager) {
        this.importRepository = importRepository;
        this.productCommandRepository = productCommandRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ProductImportConfirmResponse confirm(String principalName, int companyId, int branchId, long batchId) {
        long startedAt = System.nanoTime();
        BatchSnapshot batch = transactionTemplate.execute(status -> {
            Map<String, Object> lockedBatch = importRepository.findBatchForUpdate(companyId, batchId);
            if (lockedBatch == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "Import batch was not found");
            }
            BatchSnapshot snapshot = BatchSnapshot.from(lockedBatch);
            if (snapshot.branchId() != branchId) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_BATCH_BRANCH_MISMATCH", "Import batch does not belong to the selected branch");
            }
            if (!List.of("VALIDATED", "VALIDATED_WITH_ERRORS").contains(snapshot.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "IMPORT_BATCH_NOT_READY", "Import batch must be validated before confirmation");
            }
            assertModeCapability(principalName, companyId, branchId, snapshot.mode());
            importRepository.markBatchImporting(companyId, batchId, principalName);
            auditService.logEvent(
                    companyId,
                    branchId,
                    batchId,
                    "PRODUCT_IMPORT_CONFIRM_STARTED",
                    "Product import confirmation started",
                    principalName,
                    Map.of("mode", snapshot.mode()));
            return snapshot;
        });

        List<ProductImportStagedRow> eligibleRows = importRepository.eligibleRowsForImport(companyId, batchId);
        int skippedRows = importRepository.countIneligibleRows(companyId, batchId);
        Map<String, Integer> supplierIds = importRepository.supplierIdsByName(companyId, branchId);

        int insertedRows = 0;
        int updatedRows = 0;
        int failedRows = 0;
        for (ProductImportStagedRow row : eligibleRows) {
            try {
                if ("UPDATE".equals(row.action())) {
                    Long productId = transactionTemplate.execute(status -> {
                        if (row.existingProductId() == null || row.existingProductId() <= 0) {
                            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_UPDATE_PRODUCT_REQUIRED", "Update row has no matched product id");
                        }
                        Product product = toProduct(row.data(), supplierIds);
                        product.setProductId(row.existingProductId().intValue());
                        productCommandRepository.updateProductMetadata(product, String.valueOf(branchId), companyId);
                        importRepository.markRowUpdated(companyId, row.rowId(), row.existingProductId());
                        return row.existingProductId();
                    });
                    if (productId != null && productId > 0) {
                        updatedRows++;
                    }
                } else {
                    Long productId = transactionTemplate.execute(status -> {
                        long createdProductId = productCommandRepository.addProduct(
                                toProduct(row.data(), supplierIds),
                                String.valueOf(branchId),
                                companyId);
                        importRepository.markRowImported(companyId, row.rowId(), createdProductId);
                        return createdProductId;
                    });
                    if (productId != null && productId > 0) {
                        insertedRows++;
                    }
                }
            } catch (Exception ex) {
                failedRows++;
                importRepository.markRowImportFailed(
                        companyId,
                        branchId,
                        batchId,
                        row.rowId(),
                        row.rowNumber(),
                        "IMPORT_ROW_FAILED",
                        safeMessage(ex));
            }
        }

        int finalInsertedRows = insertedRows;
        int finalUpdatedRows = updatedRows;
        int finalFailedRows = failedRows;
        transactionTemplate.executeWithoutResult(status ->
                importRepository.finalizeImportBatch(companyId, batchId, finalInsertedRows, finalUpdatedRows, skippedRows, finalFailedRows));

        List<String> messages = new ArrayList<>();
        messages.add(insertedRows + " products imported.");
        if (updatedRows > 0) {
            messages.add(updatedRows + " products updated.");
        }
        if (skippedRows > 0) {
            messages.add(skippedRows + " invalid or duplicate rows were skipped.");
        }
        if (failedRows > 0) {
            messages.add(failedRows + " rows failed during product creation.");
        }

        String finalStatus = failedRows > 0 ? "IMPORTED_WITH_ERRORS" : "IMPORTED";
        auditService.logEvent(
                companyId,
                branchId,
                batchId,
                "PRODUCT_IMPORT_CONFIRMED",
                "Product import confirmation completed",
                principalName,
                Map.of(
                        "mode", batch.mode(),
                        "eligibleRows", eligibleRows.size(),
                        "insertedRows", insertedRows,
                        "updatedRows", updatedRows,
                        "skippedRows", skippedRows,
                        "failedRows", failedRows,
                        "status", finalStatus));
        log.info("Product import confirm completed: companyId={}, branchId={}, batchId={}, eligible={}, inserted={}, updated={}, skipped={}, failed={}, durationMs={}",
                companyId,
                branchId,
                batchId,
                eligibleRows.size(),
                insertedRows,
                updatedRows,
                skippedRows,
                failedRows,
                (System.nanoTime() - startedAt) / 1_000_000L);

        return new ProductImportConfirmResponse(
                batchId,
                finalStatus,
                eligibleRows.size(),
                insertedRows,
                updatedRows,
                skippedRows,
                failedRows,
                failedRows == 0,
                messages
        );
    }

    private Product toProduct(Map<String, String> data, Map<String, Integer> supplierIds) {
        Product product = new Product();
        product.setProductName(required(data, "product_name"));
        product.setSerial(required(data, "barcode"));
        product.setMajor(required(data, "category"));
        product.setType(defaultValue(data.get("subcategory"), "General"));
        product.setCompanyName(defaultValue(data.get("brand"), product.getMajor()));
        applyUnitCodeAndTracking(product, data);
        product.setBPrice(toInteger(data.get("purchase_price"), "purchase_price"));
        product.setRPrice(toInteger(data.get("selling_price"), "selling_price"));
        product.setLPrice(toInteger(defaultValue(data.get("wholesale_price"), data.get("purchase_price")), "wholesale_price"));
        product.setQuantity(toInteger(data.get("opening_stock_quantity"), "opening_stock_quantity"));
        product.setDesc(blankToNull(data.get("description")));
        product.setImage(blankToNull(data.get("image_url")));
        product.setPState(normalizeProductState(data.get("product_state")));
        product.setBusinessLineKey(defaultValue(data.get("business_line_key"), "MOBILE"));
        product.setTemplateKey(defaultValue(data.get("template_key"), "mobile_device"));
        product.setPricingPolicyCode(defaultValue(data.get("pricing_policy_code"), "FIXED_RETAIL"));
        product.setSupplierId(resolveSupplierId(data, supplierIds));
        product.setOnlineImageUrl(blankToNull(data.get("image_url")));
        product.setOnlineDescription(blankToNull(data.get("description")));
        product.setOnlineActive(toBoolean(data.get("active"), true));
        return product;
    }

    /**
     * unit_code carrying a tracking type (IMEI / SERIAL) is a common CSV mistake:
     * store base UOM PCS and apply the intended serialized tracking instead of
     * persisting a wrong unit code. serial_required=true also enables SERIAL tracking.
     */
    private void applyUnitCodeAndTracking(Product product, Map<String, String> data) {
        String normalizedUnitCode = normalize(data.get("unit_code"));
        switch (normalizedUnitCode) {
            case "IMEI" -> {
                product.setBaseUomCode("PCS");
                product.setTrackingType(TrackingType.IMEI);
            }
            case "SERIAL" -> {
                product.setBaseUomCode("PCS");
                product.setTrackingType(TrackingType.SERIAL);
            }
            default -> {
                product.setBaseUomCode(defaultValue(data.get("unit_code"), "PCS"));
                product.setTrackingType(toBoolean(data.get("serial_required"), false)
                        ? TrackingType.SERIAL
                        : TrackingType.QUANTITY);
            }
        }
    }

    private int resolveSupplierId(Map<String, String> data, Map<String, Integer> supplierIds) {
        String supplierName = data.get("supplier_name");
        if (supplierName == null || supplierName.isBlank()) {
            return 0;
        }
        return supplierIds.getOrDefault(normalize(supplierName), 0);
    }

    private String normalizeProductState(String value) {
        if (value == null || value.isBlank()) {
            return "New";
        }
        return "USED".equals(normalize(value)) ? "Used" : "New";
    }

    private boolean toBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (normalize(value)) {
            case "TRUE", "YES", "1" -> true;
            case "FALSE", "NO", "0" -> false;
            default -> defaultValue;
        };
    }

    private int toInteger(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_VALUE_REQUIRED", fieldName + " is required");
        }
        try {
            return new BigDecimal(value.trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_INTEGER_INVALID", fieldName + " must be an integer-compatible number");
        }
    }

    private String required(Map<String, String> data, String fieldName) {
        String value = data.get(fieldName);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_VALUE_REQUIRED", fieldName + " is required");
        }
        return value.trim();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Product import row failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private void assertModeCapability(String principalName, int companyId, int branchId, String mode) {
        switch (mode) {
            case "ADD_ONLY" -> authorizationService.assertAuthenticatedCapability(principalName, companyId, branchId, "inventory.item.create");
            case "UPDATE_ONLY" -> authorizationService.assertAuthenticatedCapability(principalName, companyId, branchId, "inventory.item.edit");
            case "UPSERT" -> {
                authorizationService.assertAuthenticatedCapability(principalName, companyId, branchId, "inventory.item.create");
                authorizationService.assertAuthenticatedCapability(principalName, companyId, branchId, "inventory.item.edit");
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MODE_INVALID", "Unsupported import mode");
        }
    }

    private record BatchSnapshot(long branchId, String mode, String status) {
        static BatchSnapshot from(Map<String, Object> row) {
            return new BatchSnapshot(
                    ((Number) row.get("branch_id")).longValue(),
                    String.valueOf(row.get("mode")),
                    String.valueOf(row.get("status"))
            );
        }
    }
}
