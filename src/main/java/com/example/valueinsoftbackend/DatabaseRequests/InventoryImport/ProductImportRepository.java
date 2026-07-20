package com.example.valueinsoftbackend.DatabaseRequests.InventoryImport;

import com.example.valueinsoftbackend.Model.InventoryImport.ParsedProductImportRow;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportBatchSummaryResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportErrorReportRow;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportErrorResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportHistoryItemResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportHistoryResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportMode;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportRowResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportRowsPageResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportRowStatus;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportStagedRow;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductImportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int previewPageMaxSize;
    private final Set<Integer> importTablesReady = ConcurrentHashMap.newKeySet();

    public ProductImportRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${inventory.import.preview-page-max-size:100}") int previewPageMaxSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.previewPageMaxSize = Math.max(1, previewPageMaxSize);
    }

    public Long createBatch(int companyId, int branchId, ProductImportMode mode, String fileName, String fileHash,
                            String createdBy, Map<String, Object> settings) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, import_type, mode, status, original_file_name, file_sha256,
                    settings_json, created_by, created_at, updated_at
                ) VALUES (
                    :companyId, :branchId, 'PRODUCT', :mode, 'VALIDATING', :fileName, :fileHash,
                    CAST(:settingsJson AS jsonb), :createdBy, NOW(), NOW()
                )
                """.formatted(batchTable(companyId));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("mode", mode.name())
                .addValue("fileName", fileName)
                .addValue("fileHash", fileHash)
                .addValue("settingsJson", toJson(settings))
                .addValue("createdBy", createdBy), keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    public void insertRows(int companyId, int branchId, long batchId, List<ParsedProductImportRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO %s (
                    batch_id, company_id, branch_id, row_number, row_hash, raw_data, normalized_data,
                    status, existing_product_id, action, sku, barcode, product_name, category, supplier_name,
                    created_at, updated_at
                ) VALUES (
                    :batchId, :companyId, :branchId, :rowNumber, :rowHash, CAST(:rawData AS jsonb), CAST(:normalizedData AS jsonb),
                    :status, :existingProductId, :action, :sku, :barcode, :productName, :category, :supplierName,
                    NOW(), NOW()
                )
                """.formatted(rowTable(companyId));

        MapSqlParameterSource[] params = rows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("batchId", batchId)
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("rowNumber", row.getRowNumber())
                        .addValue("rowHash", Integer.toHexString(row.getValues().hashCode()))
                        .addValue("rawData", toJson(row.getValues()))
                        .addValue("normalizedData", toJson(row.getNormalizedValues()))
                        .addValue("status", row.getStatus().name())
                        .addValue("existingProductId", row.getExistingProductId())
                        .addValue("action", row.getAction() == null ? "SKIP" : row.getAction())
                        .addValue("sku", blankToNull(row.value("sku")))
                        .addValue("barcode", blankToNull(row.value("barcode")))
                        .addValue("productName", blankToNull(row.value("product_name")))
                        .addValue("category", blankToNull(row.value("category")))
                        .addValue("supplierName", blankToNull(row.value("supplier_name"))))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(sql, params);

        Map<Integer, Long> rowIds = rowIdsByNumber(companyId, batchId);
        insertErrors(companyId, branchId, batchId, rowIds, rows);
    }

    public void finalizeBatch(int companyId, long batchId, int totalRows, int validRows, int warningRows,
                              int invalidRows, int duplicateRows) {
        String status = invalidRows > 0 || duplicateRows > 0 ? "VALIDATED_WITH_ERRORS" : "VALIDATED";
        String sql = """
                UPDATE %s
                SET status = :status,
                    total_rows = :totalRows,
                    valid_rows = :validRows,
                    warning_rows = :warningRows,
                    invalid_rows = :invalidRows,
                    duplicate_rows = :duplicateRows,
                    validated_at = NOW(),
                    updated_at = NOW()
                WHERE id = :batchId
                """.formatted(batchTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("totalRows", totalRows)
                .addValue("validRows", validRows)
                .addValue("warningRows", warningRows)
                .addValue("invalidRows", invalidRows)
                .addValue("duplicateRows", duplicateRows)
                .addValue("batchId", batchId));
    }

    public void updateOriginalFileStorage(int companyId, long batchId, String fileKey, long fileSize, String contentType) {
        String sql = """
                UPDATE %s
                SET original_file_key = :fileKey,
                    original_file_size = :fileSize,
                    original_content_type = :contentType,
                    original_uploaded_at = NOW(),
                    updated_at = NOW()
                WHERE id = :batchId
                """.formatted(batchTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("fileKey", fileKey)
                .addValue("fileSize", fileSize)
                .addValue("contentType", contentType));
    }

    public void updateErrorReportStorage(int companyId, long batchId, String fileKey, long fileSize) {
        String sql = """
                UPDATE %s
                SET error_report_file_key = :fileKey,
                    error_report_file_size = :fileSize,
                    error_report_generated_at = NOW(),
                    updated_at = NOW()
                WHERE id = :batchId
                """.formatted(batchTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("fileKey", fileKey)
                .addValue("fileSize", fileSize));
    }

    public Map<String, Long> existingProductsByBarcode(int companyId, Collection<String> barcodes) {
        if (barcodes.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT serial, product_id
                FROM %s
                WHERE serial IN (:barcodes)
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("barcodes", barcodes), rs -> {
            result.put(normalize(rs.getString("serial")), rs.getLong("product_id"));
        });
        return result;
    }

    /**
     * Returns (lowercased) serialized identifiers among the given candidates that
     * already exist as ACTIVE inventory units for the company. Units in a
     * re-receivable state (SOLD, RETURNED, DAMAGED, UNDER_REPAIR) are not
     * reported: the receipt pipeline can legitimately stock those in again.
     */
    public Set<String> activeSerializedIdentifiers(int companyId, Collection<String> identifiers) {
        Set<String> result = new java.util.HashSet<>();
        if (identifiers == null || identifiers.isEmpty()) {
            return result;
        }
        List<String> lowered = identifiers.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
        String sql = """
                SELECT lower(unit_identifier) AS unit_identifier, lower(imei) AS imei, lower(serial_number) AS serial_number
                FROM %s
                WHERE company_id = :companyId
                  AND status NOT IN ('SOLD', 'RETURNED', 'DAMAGED', 'UNDER_REPAIR')
                  AND (
                    lower(unit_identifier) IN (:identifiers)
                    OR lower(imei) IN (:identifiers)
                    OR lower(serial_number) IN (:identifiers)
                  )
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));
        for (int start = 0; start < lowered.size(); start += 1000) {
            List<String> chunk = lowered.subList(start, Math.min(start + 1000, lowered.size()));
            jdbcTemplate.query(sql, new MapSqlParameterSource()
                    .addValue("companyId", companyId)
                    .addValue("identifiers", chunk), rs -> {
                for (String column : List.of("unit_identifier", "imei", "serial_number")) {
                    String value = rs.getString(column);
                    if (value != null && !value.isBlank()) {
                        result.add(value);
                    }
                }
            });
        }
        return result;
    }

    public Set<String> existingCategories(int companyId, int branchId) {
        String productSql = "SELECT DISTINCT major FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " WHERE major IS NOT NULL";
        String majorSql = "SELECT * FROM " + TenantSqlIdentifiers.mainMajorTable(companyId);
        String categoryJsonSql = """
                SELECT "CategoryData"::text
                FROM %s
                WHERE "BranchId" = :branchId
                ORDER BY "CategoryJID" DESC
                LIMIT 1
                """.formatted(TenantSqlIdentifiers.categoryJsonTable(companyId));
        java.util.HashSet<String> values = new java.util.HashSet<>();
        jdbcTemplate.query(productSql, new MapSqlParameterSource(), (RowCallbackHandler) rs -> values.add(normalize(rs.getString(1))));
        jdbcTemplate.query(majorSql, new MapSqlParameterSource(), (RowCallbackHandler) rs -> values.add(normalize(rs.getString(2))));
        List<String> categoryPayloads = jdbcTemplate.queryForList(
                categoryJsonSql,
                new MapSqlParameterSource("branchId", branchId),
                String.class);
        if (!categoryPayloads.isEmpty()) {
            addCategoryJsonKeys(values, categoryPayloads.get(0));
        }
        return values;
    }

    public Set<String> existingUnits(int companyId) {
        String sql = "SELECT uom_code FROM " + TenantSqlIdentifiers.companySchema(companyId) + ".inventory_uom_unit";
        java.util.HashSet<String> values = new java.util.HashSet<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource(), (RowCallbackHandler) rs -> values.add(normalize(rs.getString(1))));
        return values;
    }

    public Set<String> existingSuppliers(int companyId, int branchId) {
        String sql = "SELECT \"SupplierName\" FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId);
        java.util.HashSet<String> values = new java.util.HashSet<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource(), (RowCallbackHandler) rs -> values.add(normalize(rs.getString(1))));
        return values;
    }

    public Map<String, Integer> supplierIdsByName(int companyId, int branchId) {
        String sql = """
                SELECT "supplierId", "SupplierName"
                FROM %s
                WHERE "SupplierName" IS NOT NULL
                """.formatted(TenantSqlIdentifiers.supplierTable(companyId, branchId));

        Map<String, Integer> values = new HashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource(), (RowCallbackHandler) rs ->
                values.put(normalize(rs.getString("SupplierName")), rs.getInt("supplierId")));
        return values;
    }

    public ProductImportRowsPageResponse findRows(int companyId, long batchId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), previewPageMaxSize);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);

        String where = "WHERE row.batch_id = :batchId";
        if (status != null && !status.isBlank()) {
            where += " AND row.status = :status";
            params.addValue("status", status.trim().toUpperCase());
        }

        String countSql = "SELECT COUNT(*) FROM " + rowTable(companyId) + " row " + where;
        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);

        String sql = """
                SELECT row.id, row.row_number, row.status, row.action, row.product_name, row.sku, row.barcode, row.category,
                       row.supplier_name, row.existing_product_id,
                       COALESCE(jsonb_agg(
                           jsonb_build_object(
                               'fieldName', err.field_name,
                               'code', err.error_code,
                               'message', err.error_message,
                               'severity', err.severity,
                               'rawValue', err.raw_value
                           ) ORDER BY err.id
                       ) FILTER (WHERE err.id IS NOT NULL), '[]'::jsonb)::text AS errors_json
                FROM %s row
                LEFT JOIN %s err ON err.row_id = row.id
                %s
                GROUP BY row.id, row.row_number, row.status, row.action, row.product_name, row.sku, row.barcode, row.category,
                         row.supplier_name, row.existing_product_id
                ORDER BY row.row_number ASC
                LIMIT :limit OFFSET :offset
                """.formatted(rowTable(companyId), errorTable(companyId), where);

        List<ProductImportRowResponse> items = jdbcTemplate.query(sql, params, (rs, rowNum) -> new ProductImportRowResponse(
                rs.getLong("id"),
                rs.getInt("row_number"),
                rs.getString("status"),
                rs.getString("action"),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getString("barcode"),
                rs.getString("category"),
                rs.getString("supplier_name"),
                rs.getObject("existing_product_id") == null ? null : rs.getLong("existing_product_id"),
                parseErrors(rs.getString("errors_json"))
        ));
        return new ProductImportRowsPageResponse(items, safePage, safeSize, total == null ? 0 : total);
    }

    public ProductImportBatchSummaryResponse findBatchSummary(int companyId, long batchId) {
        String sql = """
                SELECT id, company_id, branch_id, import_type, mode, status,
                       total_rows, valid_rows, warning_rows, invalid_rows, duplicate_rows,
                       inserted_rows, updated_rows, skipped_rows, failed_rows,
                       original_file_name, created_by, confirmed_by,
                       created_at, validated_at, confirmed_at, completed_at, updated_at
                FROM %s
                WHERE id = :batchId
                """.formatted(batchTable(companyId));

        List<ProductImportBatchSummaryResponse> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("batchId", batchId),
                (rs, rowNum) -> new ProductImportBatchSummaryResponse(
                        rs.getLong("id"),
                        rs.getInt("company_id"),
                        rs.getInt("branch_id"),
                        rs.getString("import_type"),
                        rs.getString("mode"),
                        rs.getString("status"),
                        rs.getInt("total_rows"),
                        rs.getInt("valid_rows"),
                        rs.getInt("warning_rows"),
                        rs.getInt("invalid_rows"),
                        rs.getInt("duplicate_rows"),
                        rs.getInt("inserted_rows"),
                        rs.getInt("updated_rows"),
                        rs.getInt("skipped_rows"),
                        rs.getInt("failed_rows"),
                        rs.getString("original_file_name"),
                        rs.getString("created_by"),
                        rs.getString("confirmed_by"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class),
                        rs.getObject("validated_at", java.time.OffsetDateTime.class),
                        rs.getObject("confirmed_at", java.time.OffsetDateTime.class),
                        rs.getObject("completed_at", java.time.OffsetDateTime.class),
                        rs.getObject("updated_at", java.time.OffsetDateTime.class),
                        isConfirmable(rs.getString("status"), rs.getInt("valid_rows"), rs.getInt("warning_rows"))
                ));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ProductImportHistoryResponse findHistory(int companyId, int branchId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), previewPageMaxSize);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);

        String countSql = "SELECT COUNT(*) FROM " + batchTable(companyId) + " WHERE branch_id = :branchId";
        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);

        String sql = """
                SELECT id, company_id, branch_id, mode, status, original_file_name, original_file_size,
                       original_file_key, error_report_file_key, total_rows, valid_rows, warning_rows,
                       invalid_rows, duplicate_rows, inserted_rows, updated_rows, failed_rows,
                       created_by, created_at, completed_at, updated_at
                FROM %s
                WHERE branch_id = :branchId
                ORDER BY updated_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(batchTable(companyId));

        List<ProductImportHistoryItemResponse> items = jdbcTemplate.query(sql, params, (rs, rowNum) -> new ProductImportHistoryItemResponse(
                rs.getLong("id"),
                rs.getInt("company_id"),
                rs.getInt("branch_id"),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getString("original_file_name"),
                rs.getObject("original_file_size") == null ? null : rs.getLong("original_file_size"),
                rs.getString("original_file_key") != null && !rs.getString("original_file_key").isBlank(),
                rs.getString("error_report_file_key") != null && !rs.getString("error_report_file_key").isBlank(),
                rs.getInt("total_rows"),
                rs.getInt("valid_rows"),
                rs.getInt("warning_rows"),
                rs.getInt("invalid_rows"),
                rs.getInt("duplicate_rows"),
                rs.getInt("inserted_rows"),
                rs.getInt("updated_rows"),
                rs.getInt("failed_rows"),
                rs.getString("created_by"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("completed_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        ));

        return new ProductImportHistoryResponse(items, safePage, safeSize, total == null ? 0 : total);
    }

    public String findBatchFileKey(int companyId, int branchId, long batchId, String fileType) {
        String column = "ERROR_REPORT".equals(fileType) ? "error_report_file_key" : "original_file_key";
        String sql = """
                SELECT %s
                FROM %s
                WHERE id = :batchId
                  AND branch_id = :branchId
                """.formatted(column, batchTable(companyId));

        List<String> keys = jdbcTemplate.queryForList(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("branchId", branchId), String.class);
        return keys.isEmpty() ? null : keys.get(0);
    }

    public Map<String, Object> findBatchForUpdate(int companyId, long batchId) {
        String sql = """
                SELECT id, company_id, branch_id, mode, status, total_rows, valid_rows, warning_rows,
                       invalid_rows, duplicate_rows, inserted_rows, updated_rows, skipped_rows, failed_rows
                FROM %s
                WHERE id = :batchId
                FOR UPDATE
                """.formatted(batchTable(companyId));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("batchId", batchId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findBatch(int companyId, long batchId) {
        String sql = """
                SELECT id, company_id, branch_id, mode, status
                FROM %s
                WHERE id = :batchId
                """.formatted(batchTable(companyId));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("batchId", batchId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void markBatchImporting(int companyId, long batchId, String confirmedBy) {
        String sql = """
                UPDATE %s
                SET status = 'IMPORTING',
                    confirmed_by = :confirmedBy,
                    confirmed_at = NOW(),
                    updated_at = NOW()
                WHERE id = :batchId
                """.formatted(batchTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("confirmedBy", confirmedBy));
    }

    public List<ProductImportStagedRow> eligibleRowsForImport(int companyId, long batchId) {
        String sql = """
                SELECT id, row_number, status, action, existing_product_id, normalized_data::text AS normalized_data_json
                FROM %s
                WHERE batch_id = :batchId
                  AND status IN ('VALID', 'WARNING')
                  AND action IN ('INSERT', 'UPDATE', 'RECEIVE')
                ORDER BY row_number ASC
                """.formatted(rowTable(companyId));

        return jdbcTemplate.query(sql, new MapSqlParameterSource("batchId", batchId), (rs, rowNum) -> new ProductImportStagedRow(
                rs.getLong("id"),
                rs.getInt("row_number"),
                rs.getString("status"),
                rs.getString("action"),
                rs.getObject("existing_product_id") == null ? null : rs.getLong("existing_product_id"),
                parseStringMap(rs.getString("normalized_data_json"))
        ));
    }

    public int countIneligibleRows(int companyId, long batchId) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE batch_id = :batchId
                  AND status NOT IN ('VALID', 'WARNING')
                """.formatted(rowTable(companyId));

        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("batchId", batchId), Integer.class);
        return count == null ? 0 : count;
    }

    public void markRowImported(int companyId, long rowId, long productId) {
        String sql = """
                UPDATE %s
                SET status = 'IMPORTED',
                    created_product_id = :productId,
                    updated_at = NOW()
                WHERE id = :rowId
                """.formatted(rowTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("rowId", rowId)
                .addValue("productId", productId));
    }

    public void markRowUpdated(int companyId, long rowId, long productId) {
        String sql = """
                UPDATE %s
                SET status = 'UPDATED',
                    existing_product_id = :productId,
                    updated_at = NOW()
                WHERE id = :rowId
                """.formatted(rowTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("rowId", rowId)
                .addValue("productId", productId));
    }

    public void markRowImportFailed(int companyId, int branchId, long batchId, long rowId, int rowNumber,
                                    String errorCode, String errorMessage) {
        String rowSql = """
                UPDATE %s
                SET status = 'IMPORT_FAILED',
                    updated_at = NOW()
                WHERE id = :rowId
                """.formatted(rowTable(companyId));
        jdbcTemplate.update(rowSql, new MapSqlParameterSource("rowId", rowId));

        String errorSql = """
                INSERT INTO %s (
                    batch_id, row_id, company_id, branch_id, row_number, field_name, error_code,
                    error_message, severity, raw_value, created_at
                ) VALUES (
                    :batchId, :rowId, :companyId, :branchId, :rowNumber, NULL, :errorCode,
                    :errorMessage, 'ERROR', NULL, NOW()
                )
                """.formatted(errorTable(companyId));
        jdbcTemplate.update(errorSql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("rowId", rowId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("rowNumber", rowNumber)
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", errorMessage));
    }

    public void finalizeImportBatch(int companyId, long batchId, int insertedRows, int updatedRows,
                                    int skippedRows, int failedRows) {
        String status = failedRows > 0 ? "IMPORTED_WITH_ERRORS" : "IMPORTED";
        String sql = """
                UPDATE %s
                SET status = :status,
                    inserted_rows = :insertedRows,
                    updated_rows = :updatedRows,
                    skipped_rows = :skippedRows,
                    failed_rows = :failedRows,
                    completed_at = NOW(),
                    updated_at = NOW()
                WHERE id = :batchId
                """.formatted(batchTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("status", status)
                .addValue("insertedRows", insertedRows)
                .addValue("updatedRows", updatedRows)
                .addValue("skippedRows", skippedRows)
                .addValue("failedRows", failedRows));
    }

    public void insertAuditLog(int companyId, int branchId, Long batchId, String eventType,
                               String eventMessage, String actorName, Map<String, Object> payload) {
        String sql = """
                INSERT INTO %s (
                    batch_id, company_id, branch_id, event_type, event_message,
                    actor_name, payload_json, created_at
                ) VALUES (
                    :batchId, :companyId, :branchId, :eventType, :eventMessage,
                    :actorName, CAST(:payloadJson AS jsonb), NOW()
                )
                """.formatted(TenantSqlIdentifiers.inventoryImportAuditLogTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("eventType", eventType)
                .addValue("eventMessage", eventMessage)
                .addValue("actorName", actorName)
                .addValue("payloadJson", toJson(payload == null ? Map.of() : payload)));
    }

    public int cleanupExpiredBatches(int retentionDays, int maxBatchesPerTenant) {
        if (retentionDays <= 0 || maxBatchesPerTenant <= 0) {
            return 0;
        }

        String schemasSql = """
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name LIKE 'c\\_%' ESCAPE '\\'
                ORDER BY schema_name
                """;
        List<String> schemas = jdbcTemplate.getJdbcTemplate().queryForList(schemasSql, String.class);

        int deleted = 0;
        for (String schemaName : schemas) {
            TenantSqlIdentifiers.requireSchemaName(schemaName);
            String sql = """
                    DELETE FROM %s.inventory_import_batch
                    WHERE id IN (
                        SELECT id
                        FROM %s.inventory_import_batch
                        WHERE status IN ('IMPORTED', 'IMPORTED_WITH_ERRORS', 'FAILED', 'CANCELLED')
                          AND created_at < NOW() - (:retentionDays * INTERVAL '1 day')
                        ORDER BY created_at ASC
                        LIMIT :limit
                    )
                    """.formatted(schemaName, schemaName);

            deleted += jdbcTemplate.update(sql, new MapSqlParameterSource()
                    .addValue("retentionDays", retentionDays)
                    .addValue("limit", maxBatchesPerTenant));
        }
        return deleted;
    }

    public List<ProductImportErrorReportRow> errorReportRows(int companyId, long batchId) {
        String sql = """
                SELECT row.row_number,
                       row.status,
                       row.raw_data::text AS raw_data_json,
                       COALESCE(jsonb_agg(
                           jsonb_build_object(
                               'fieldName', err.field_name,
                               'code', err.error_code,
                               'message', err.error_message,
                               'severity', err.severity,
                               'rawValue', err.raw_value
                           ) ORDER BY err.id
                       ) FILTER (WHERE err.id IS NOT NULL), '[]'::jsonb)::text AS errors_json
                FROM %s row
                LEFT JOIN %s err ON err.row_id = row.id
                WHERE row.batch_id = :batchId
                  AND (
                      row.status IN ('INVALID', 'DUPLICATE', 'IMPORT_FAILED', 'SKIPPED')
                      OR err.id IS NOT NULL
                  )
                GROUP BY row.id, row.row_number, row.status, row.raw_data
                ORDER BY row.row_number ASC
                """.formatted(rowTable(companyId), errorTable(companyId));

        return jdbcTemplate.query(sql, new MapSqlParameterSource("batchId", batchId), (rs, rowNum) -> new ProductImportErrorReportRow(
                rs.getInt("row_number"),
                rs.getString("status"),
                parseStringMap(rs.getString("raw_data_json")),
                parseErrors(rs.getString("errors_json"))
        ));
    }

    private void insertErrors(int companyId, int branchId, long batchId, Map<Integer, Long> rowIds, List<ParsedProductImportRow> rows) {
        List<MapSqlParameterSource> params = new ArrayList<>();
        for (ParsedProductImportRow row : rows) {
            Long rowId = rowIds.get(row.getRowNumber());
            for (ProductImportErrorResponse error : row.getErrors()) {
                params.add(new MapSqlParameterSource()
                        .addValue("batchId", batchId)
                        .addValue("rowId", rowId)
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("rowNumber", row.getRowNumber())
                        .addValue("fieldName", error.fieldName())
                        .addValue("errorCode", error.code())
                        .addValue("errorMessage", error.message())
                        .addValue("severity", error.severity())
                        .addValue("rawValue", error.rawValue()));
            }
        }
        if (params.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO %s (
                    batch_id, row_id, company_id, branch_id, row_number, field_name, error_code,
                    error_message, severity, raw_value, created_at
                ) VALUES (
                    :batchId, :rowId, :companyId, :branchId, :rowNumber, :fieldName, :errorCode,
                    :errorMessage, :severity, :rawValue, NOW()
                )
                """.formatted(errorTable(companyId));
        jdbcTemplate.batchUpdate(sql, params.toArray(MapSqlParameterSource[]::new));
    }

    private Map<Integer, Long> rowIdsByNumber(int companyId, long batchId) {
        String sql = "SELECT id, row_number FROM " + rowTable(companyId) + " WHERE batch_id = :batchId";
        Map<Integer, Long> ids = new LinkedHashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("batchId", batchId),
                (RowCallbackHandler) rs -> ids.put(rs.getInt("row_number"), rs.getLong("id")));
        return ids;
    }

    private List<ProductImportErrorResponse> parseErrors(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProductImportErrorResponse>>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, String> parseStringMap(String json) {
        try {
            Map<String, Object> values = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new LinkedHashMap<>();
            values.forEach((key, value) -> result.put(key, value == null ? "" : String.valueOf(value)));
            return result;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private void addCategoryJsonKeys(Set<String> values, String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return;
        }

        try {
            JsonNode node = normalizeCategoryNode(rawPayload.trim());
            appendCategoryJsonKeys(values, node);
        } catch (JsonProcessingException ex) {
            // Invalid legacy category JSON should not make import validation fail globally.
        }
    }

    private JsonNode normalizeCategoryNode(String payload) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(payload);
        if (node.isTextual()) {
            return objectMapper.readTree(node.asText());
        }
        if (node.isArray()) {
            if (node.size() == 0) {
                return objectMapper.createObjectNode();
            }
            JsonNode firstNode = node.get(0);
            if (node.size() == 1 && firstNode.isTextual()) {
                return objectMapper.readTree(firstNode.asText());
            }
            if (node.size() == 1 && firstNode.isObject()) {
                return firstNode;
            }
        }
        return node;
    }

    private void appendCategoryJsonKeys(Set<String> values, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            if (node.has("categoryData")) {
                appendCategoryJsonKeys(values, node.get("categoryData"));
                return;
            }

            if (node.hasNonNull("key")) {
                values.add(normalize(node.get("key").asText()));
                return;
            }

            node.fields().forEachRemaining(entry -> {
                if (!"branchGroups".equals(entry.getKey()) && !"groups".equals(entry.getKey())) {
                    values.add(normalize(entry.getKey()));
                }
            });
            return;
        }

        if (node.isArray()) {
            for (JsonNode entry : node) {
                if (entry != null && entry.isTextual()) {
                    addCategoryJsonKeys(values, entry.asText());
                } else {
                    appendCategoryJsonKeys(values, entry);
                }
            }
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize import data", ex);
        }
    }

    private String batchTable(int companyId) {
        ensureImportTables(companyId);
        return TenantSqlIdentifiers.inventoryImportBatchTable(companyId);
    }

    private String rowTable(int companyId) {
        ensureImportTables(companyId);
        return TenantSqlIdentifiers.inventoryImportRowTable(companyId);
    }

    private String errorTable(int companyId) {
        ensureImportTables(companyId);
        return TenantSqlIdentifiers.inventoryImportErrorTable(companyId);
    }

    private void ensureImportTables(int companyId) {
        if (importTablesReady.contains(companyId)) {
            return;
        }

        synchronized (importTablesReady) {
            if (importTablesReady.contains(companyId)) {
                return;
            }

            String schemaName = TenantSqlIdentifiers.companySchema(companyId);
            jdbcTemplate.getJdbcOperations()
                    .execute("SELECT public.create_inventory_product_import_tables_for_tenant('" + schemaName + "')");
            importTablesReady.add(companyId);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isConfirmable(String status, int validRows, int warningRows) {
        return ("VALIDATED".equals(status) || "VALIDATED_WITH_ERRORS".equals(status)) && validRows + warningRows > 0;
    }
}
