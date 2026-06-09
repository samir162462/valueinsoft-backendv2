package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentItemPreviewResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class PriceAdjustmentBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PriceAdjustmentBatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdjustmentProductRow> findProductRows(ProductScopeQuery query) {
        int safeSize = Math.min(Math.max(1, query.maxProducts()), 500);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", query.companyId())
                .addValue("branchId", query.branchId())
                .addValue("limit", safeSize);
        List<String> conditions = new ArrayList<>();
        conditions.add("p.company_id = :companyId");

        if (query.queryText() != null && !query.queryText().isBlank()) {
            conditions.add("(p.product_name ILIKE :queryText OR p.serial ILIKE :queryText OR p.barcode ILIKE :queryText)");
            params.addValue("queryText", "%" + query.queryText().trim() + "%");
        }
        if (query.productIds() != null && !query.productIds().isEmpty()) {
            conditions.add("p.product_id IN (:productIds)");
            params.addValue("productIds", query.productIds());
        }
        String category = firstText(query.category(), query.major());
        if (category != null) {
            conditions.add("LOWER(p.major) = :category");
            params.addValue("category", category.toLowerCase(Locale.ROOT));
        }
        if (query.businessLineKey() != null && !query.businessLineKey().isBlank()) {
            conditions.add("LOWER(p.business_line_key) = :businessLineKey");
            params.addValue("businessLineKey", query.businessLineKey().trim().toLowerCase(Locale.ROOT));
        }
        if (query.templateKey() != null && !query.templateKey().isBlank()) {
            conditions.add("LOWER(p.template_key) = :templateKey");
            params.addValue("templateKey", query.templateKey().trim().toLowerCase(Locale.ROOT));
        }
        if (query.supplierId() != null) {
            conditions.add("p.supplier_id = :supplierId");
            params.addValue("supplierId", query.supplierId());
        }

        String sql = """
                SELECT
                    p.product_id,
                    p.product_name,
                    p.retail_price::numeric AS retail_price,
                    p.lowest_price::numeric AS lowest_price,
                    p.buying_price::numeric AS buying_price
                FROM %s p
                WHERE %s
                ORDER BY p.product_name ASC, p.product_id ASC
                LIMIT :limit
                """.formatted(
                TenantSqlIdentifiers.inventoryProductTable(query.companyId()),
                String.join(" AND ", conditions)
        );
        return jdbcTemplate.query(sql, params, PRODUCT_MAPPER);
    }

    public List<AdjustmentProductRow> findRecommendationRows(int companyId, int branchId, long runId,
                                                             List<Long> recommendationItemIds, int maxProducts) {
        int safeSize = Math.min(Math.max(1, maxProducts), 500);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("runId", runId)
                .addValue("itemIds", recommendationItemIds)
                .addValue("limit", safeSize);
        String itemFilter = recommendationItemIds == null || recommendationItemIds.isEmpty()
                ? ""
                : " AND rec.item_id IN (:itemIds) ";
        String sql = """
                SELECT
                    rec.product_id,
                    rec.item_id AS recommendation_item_id,
                    rec.product_name,
                    rec.old_retail_price AS retail_price,
                    rec.old_lowest_price AS lowest_price,
                    rec.buying_price,
                    rec.suggested_retail_price,
                    rec.suggested_lowest_price
                FROM %s rec
                WHERE rec.run_id = :runId
                  AND rec.branch_id = :branchId
                  AND rec.recommendation_status IN ('RECOMMENDED', 'WARNING')
                  AND rec.suggested_retail_price IS NOT NULL
                  AND rec.suggested_lowest_price IS NOT NULL
                  %s
                ORDER BY rec.product_name ASC, rec.product_id ASC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.inventoryPriceRecommendationItemTable(companyId), itemFilter);
        return jdbcTemplate.query(sql, params, RECOMMENDATION_MAPPER);
    }

    public long createBatch(int companyId, int branchId, String sourceType, Long sourceRunId, String status,
                            String adjustmentMode, String direction, BigDecimal adjustmentValue,
                            String priceTargetsJson, String scopeJson, String actorName, String reason) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, source_type, source_run_id, status,
                    adjustment_mode, direction, adjustment_value, price_targets,
                    scope_json, policy_snapshot_json, reason, created_by, created_at, updated_at
                ) VALUES (
                    :companyId, :branchId, :sourceType, :sourceRunId, :status,
                    :adjustmentMode, :direction, :adjustmentValue, CAST(:priceTargetsJson AS jsonb),
                    CAST(:scopeJson AS jsonb), '{}'::jsonb, :reason, :actorName, NOW(), NOW()
                )
                RETURNING batch_id
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId));
        Long id = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("sourceType", sourceType)
                .addValue("sourceRunId", sourceRunId)
                .addValue("status", status)
                .addValue("adjustmentMode", adjustmentMode)
                .addValue("direction", direction)
                .addValue("adjustmentValue", adjustmentValue)
                .addValue("priceTargetsJson", priceTargetsJson)
                .addValue("scopeJson", scopeJson)
                .addValue("reason", reason)
                .addValue("actorName", actorName), Long.class);
        return id == null ? 0L : id;
    }

    public void insertItem(int companyId, int branchId, long batchId, AdjustmentItemDraft item) {
        String sql = """
                INSERT INTO %s (
                    batch_id, company_id, branch_id, product_id, recommendation_item_id, product_name,
                    old_retail_price, new_retail_price, old_lowest_price, new_lowest_price,
                    buying_price_snapshot, delta_amount, delta_pct, expected_margin_pct, status,
                    reason_codes, warning_codes, blocked_codes, created_at
                ) VALUES (
                    :batchId, :companyId, :branchId, :productId, :recommendationItemId, :productName,
                    :oldRetailPrice, :newRetailPrice, :oldLowestPrice, :newLowestPrice,
                    :buyingPriceSnapshot, :deltaAmount, :deltaPct, :expectedMarginPct, :status,
                    CAST(:reasonCodes AS jsonb), CAST(:warningCodes AS jsonb), CAST(:blockedCodes AS jsonb), NOW()
                )
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentItemTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productId", item.productId())
                .addValue("recommendationItemId", item.recommendationItemId())
                .addValue("productName", item.productName())
                .addValue("oldRetailPrice", item.oldRetailPrice())
                .addValue("newRetailPrice", item.newRetailPrice())
                .addValue("oldLowestPrice", item.oldLowestPrice())
                .addValue("newLowestPrice", item.newLowestPrice())
                .addValue("buyingPriceSnapshot", item.buyingPriceSnapshot())
                .addValue("deltaAmount", item.deltaAmount())
                .addValue("deltaPct", item.deltaPct())
                .addValue("expectedMarginPct", item.expectedMarginPct())
                .addValue("status", item.status())
                .addValue("reasonCodes", jsonArray(item.reasonCodes()))
                .addValue("warningCodes", jsonArray(item.warningCodes()))
                .addValue("blockedCodes", jsonArray(item.blockedCodes())));
    }

    public void updateCounts(int companyId, long batchId, int total, int valid, int warning, int blocked) {
        String sql = """
                UPDATE %s
                SET total_items = :total,
                    valid_items = :valid,
                    warning_items = :warning,
                    blocked_items = :blocked,
                    updated_at = NOW()
                WHERE batch_id = :batchId
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("total", total)
                .addValue("valid", valid)
                .addValue("warning", warning)
                .addValue("blocked", blocked)
                .addValue("batchId", batchId));
    }

    public PriceAdjustmentBatchResponse findBatch(int companyId, long batchId) {
        String sql = "SELECT * FROM %s WHERE batch_id = :batchId"
                .formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource().addValue("batchId", batchId), BATCH_MAPPER);
    }

    public PriceAdjustmentBatchResponse lockBatch(int companyId, long batchId) {
        String sql = "SELECT * FROM %s WHERE batch_id = :batchId FOR UPDATE"
                .formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource().addValue("batchId", batchId), BATCH_MAPPER);
    }

    public void markSubmitted(int companyId, long batchId, String actorName) {
        updateLifecycle(companyId, batchId, "PENDING_APPROVAL", "submitted_by", "submitted_at", actorName);
    }

    public void markApproved(int companyId, long batchId, String actorName) {
        updateLifecycle(companyId, batchId, "APPROVED", "approved_by", "approved_at", actorName);
    }

    public void markRejected(int companyId, long batchId, String actorName) {
        updateLifecycle(companyId, batchId, "REJECTED", "rejected_by", "rejected_at", actorName);
    }

    public void markCancelled(int companyId, long batchId) {
        String sql = """
                UPDATE %s
                SET status = 'CANCELLED',
                    updated_at = NOW()
                WHERE batch_id = :batchId
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("batchId", batchId));
    }

    public void markApplying(int companyId, long batchId, String actorName) {
        updateLifecycle(companyId, batchId, "APPLYING", "applied_by", null, actorName);
    }

    public void completeApply(int companyId, long batchId, String status, int appliedItems, int failedItems) {
        String sql = """
                UPDATE %s
                SET status = :status,
                    applied_items = :appliedItems,
                    failed_items = :failedItems,
                    applied_at = NOW(),
                    updated_at = NOW()
                WHERE batch_id = :batchId
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("appliedItems", appliedItems)
                .addValue("failedItems", failedItems)
                .addValue("batchId", batchId));
    }

    public List<ApplyItemRow> findApplicableItems(int companyId, int branchId, long batchId) {
        String sql = """
                SELECT *
                FROM %s
                WHERE batch_id = :batchId
                  AND branch_id = :branchId
                  AND status IN ('VALID', 'WARNING')
                ORDER BY item_id ASC
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentItemTable(companyId));
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("branchId", branchId), APPLY_ITEM_MAPPER);
    }

    public ProductPriceRow findProductPrice(int companyId, long productId) {
        String sql = """
                SELECT product_id, retail_price::numeric AS retail_price, lowest_price::numeric AS lowest_price,
                       buying_price::numeric AS buying_price
                FROM %s
                WHERE product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource().addValue("productId", productId), PRODUCT_PRICE_MAPPER);
    }

    public int updateProductPriceIfCurrent(int companyId, ApplyItemRow item) {
        String sql = """
                UPDATE %s
                SET retail_price = :newRetailPrice,
                    lowest_price = :newLowestPrice,
                    updated_at = CURRENT_TIMESTAMP
                WHERE product_id = :productId
                  AND retail_price = :oldRetailPrice
                  AND lowest_price = :oldLowestPrice
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("newRetailPrice", item.newRetailPrice())
                .addValue("newLowestPrice", item.newLowestPrice())
                .addValue("productId", item.productId())
                .addValue("oldRetailPrice", item.oldRetailPrice())
                .addValue("oldLowestPrice", item.oldLowestPrice()));
    }

    public void markItemApplied(int companyId, long itemId) {
        markItemResult(companyId, itemId, "APPLIED", null);
    }

    public void markItemSkipped(int companyId, long itemId, String message) {
        markItemResult(companyId, itemId, "SKIPPED", message);
    }

    public void markItemFailed(int companyId, long itemId, String message) {
        markItemResult(companyId, itemId, "FAILED", message);
    }

    private void markItemResult(int companyId, long itemId, String status, String message) {
        String sql = """
                UPDATE %s
                SET status = :status,
                    apply_error = :message,
                    applied_at = CASE WHEN :status = 'APPLIED' THEN NOW() ELSE applied_at END
                WHERE item_id = :itemId
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentItemTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("message", message)
                .addValue("itemId", itemId));
    }

    private void updateLifecycle(int companyId, long batchId, String status, String actorColumn, String timeColumn, String actorName) {
        String timestampAssignment = timeColumn == null ? "" : ", " + timeColumn + " = NOW()";
        String sql = """
                UPDATE %s
                SET status = :status,
                    %s = :actorName
                    %s,
                    updated_at = NOW()
                WHERE batch_id = :batchId
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId), actorColumn, timestampAssignment);
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("actorName", actorName)
                .addValue("batchId", batchId));
    }

    public BatchesPage findBatches(int companyId, int branchId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int offset = safePage * safeSize;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("status", status)
                .addValue("limit", safeSize)
                .addValue("offset", offset);
        String whereStatus = status == null || status.isBlank() ? "" : " AND status = :status ";
        String table = TenantSqlIdentifiers.inventoryPriceAdjustmentBatchTable(companyId);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE branch_id = :branchId " + whereStatus,
                params,
                Long.class
        );
        List<PriceAdjustmentBatchResponse> items = jdbcTemplate.query(
                "SELECT * FROM " + table + " WHERE branch_id = :branchId " + whereStatus +
                        " ORDER BY created_at DESC, batch_id DESC LIMIT :limit OFFSET :offset",
                params,
                BATCH_MAPPER
        );
        long totalItems = total == null ? 0 : total;
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / safeSize);
        return new BatchesPage(safePage, safeSize, totalItems, totalPages, items);
    }

    public ItemsPage findItems(int companyId, int branchId, long batchId, String status, int page, int size, boolean includeCost) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int offset = safePage * safeSize;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("branchId", branchId)
                .addValue("status", status)
                .addValue("limit", safeSize)
                .addValue("offset", offset);
        String whereStatus = status == null || status.isBlank() ? "" : " AND status = :status ";
        String table = TenantSqlIdentifiers.inventoryPriceAdjustmentItemTable(companyId);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE batch_id = :batchId AND branch_id = :branchId " + whereStatus,
                params,
                Long.class
        );
        List<PriceAdjustmentItemPreviewResponse> items = jdbcTemplate.query(
                "SELECT * FROM " + table + " WHERE batch_id = :batchId AND branch_id = :branchId " + whereStatus +
                        " ORDER BY product_name ASC, product_id ASC LIMIT :limit OFFSET :offset",
                params,
                itemMapper(includeCost)
        );
        long totalItems = total == null ? 0 : total;
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / safeSize);
        return new ItemsPage(safePage, safeSize, totalItems, totalPages, items);
    }

    public int deleteItem(int companyId, int branchId, long batchId, long itemId) {
        String sql = """
                DELETE FROM %s
                WHERE item_id = :itemId
                  AND batch_id = :batchId
                  AND branch_id = :branchId
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentItemTable(companyId));
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("batchId", batchId)
                .addValue("branchId", branchId));
    }

    public void recalculateAndUpdateCounts(int companyId, long batchId) {
        String sql = """
                SELECT
                    COUNT(*) as total,
                    COUNT(CASE WHEN status = 'VALID' THEN 1 END) as valid,
                    COUNT(CASE WHEN status = 'WARNING' THEN 1 END) as warning,
                    COUNT(CASE WHEN status = 'BLOCKED' THEN 1 END) as blocked
                FROM %s
                WHERE batch_id = :batchId
                """.formatted(TenantSqlIdentifiers.inventoryPriceAdjustmentItemTable(companyId));
        java.util.Map<String, Object> counts = jdbcTemplate.queryForMap(sql, new MapSqlParameterSource().addValue("batchId", batchId));
        int total = ((Number) counts.get("total")).intValue();
        int valid = counts.get("valid") != null ? ((Number) counts.get("valid")).intValue() : 0;
        int warning = counts.get("warning") != null ? ((Number) counts.get("warning")).intValue() : 0;
        int blocked = counts.get("blocked") != null ? ((Number) counts.get("blocked")).intValue() : 0;
        updateCounts(companyId, batchId, total, valid, warning, blocked);
    }

    private static final RowMapper<AdjustmentProductRow> PRODUCT_MAPPER = (rs, rowNum) -> new AdjustmentProductRow(
            rs.getLong("product_id"),
            null,
            rs.getString("product_name"),
            rs.getBigDecimal("retail_price"),
            rs.getBigDecimal("lowest_price"),
            rs.getBigDecimal("buying_price"),
            null,
            null
    );

    private static final RowMapper<AdjustmentProductRow> RECOMMENDATION_MAPPER = (rs, rowNum) -> new AdjustmentProductRow(
            rs.getLong("product_id"),
            rs.getLong("recommendation_item_id"),
            rs.getString("product_name"),
            rs.getBigDecimal("retail_price"),
            rs.getBigDecimal("lowest_price"),
            rs.getBigDecimal("buying_price"),
            rs.getBigDecimal("suggested_retail_price"),
            rs.getBigDecimal("suggested_lowest_price")
    );

    private static final RowMapper<PriceAdjustmentBatchResponse> BATCH_MAPPER = (rs, rowNum) -> new PriceAdjustmentBatchResponse(
            rs.getLong("batch_id"),
            rs.getInt("company_id"),
            rs.getInt("branch_id"),
            rs.getString("source_type"),
            getLongOrNull(rs, "source_run_id"),
            rs.getString("status"),
            rs.getString("adjustment_mode"),
            rs.getString("direction"),
            rs.getBigDecimal("adjustment_value"),
            rs.getString("price_targets"),
            rs.getInt("total_items"),
            rs.getInt("valid_items"),
            rs.getInt("warning_items"),
            rs.getInt("blocked_items"),
            rs.getInt("applied_items"),
            rs.getInt("failed_items"),
            rs.getString("reason"),
            rs.getString("created_by"),
            rs.getString("submitted_by"),
            rs.getString("approved_by"),
            rs.getString("rejected_by"),
            rs.getString("applied_by"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("submitted_at", OffsetDateTime.class),
            rs.getObject("approved_at", OffsetDateTime.class),
            rs.getObject("rejected_at", OffsetDateTime.class),
            rs.getObject("applied_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private static RowMapper<PriceAdjustmentItemPreviewResponse> itemMapper(boolean includeCost) {
        return (rs, rowNum) -> new PriceAdjustmentItemPreviewResponse(
                rs.getLong("item_id"),
                rs.getLong("batch_id"),
                rs.getLong("product_id"),
                getLongOrNull(rs, "recommendation_item_id"),
                rs.getString("product_name"),
                rs.getBigDecimal("old_retail_price"),
                rs.getBigDecimal("new_retail_price"),
                rs.getBigDecimal("old_lowest_price"),
                rs.getBigDecimal("new_lowest_price"),
                includeCost ? rs.getBigDecimal("buying_price_snapshot") : null,
                rs.getBigDecimal("delta_amount"),
                rs.getBigDecimal("delta_pct"),
                includeCost ? rs.getBigDecimal("expected_margin_pct") : null,
                rs.getString("status"),
                jsonArrayToList(rs.getString("reason_codes")),
                jsonArrayToList(rs.getString("warning_codes")),
                jsonArrayToList(rs.getString("blocked_codes")),
                rs.getString("apply_error"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("applied_at", OffsetDateTime.class)
        );
    }

    private static final RowMapper<ApplyItemRow> APPLY_ITEM_MAPPER = (rs, rowNum) -> new ApplyItemRow(
            rs.getLong("item_id"),
            rs.getLong("batch_id"),
            rs.getLong("product_id"),
            getLongOrNull(rs, "recommendation_item_id"),
            rs.getString("product_name"),
            rs.getBigDecimal("old_retail_price"),
            rs.getBigDecimal("new_retail_price"),
            rs.getBigDecimal("old_lowest_price"),
            rs.getBigDecimal("new_lowest_price"),
            rs.getBigDecimal("buying_price_snapshot"),
            rs.getBigDecimal("delta_amount"),
            rs.getBigDecimal("delta_pct"),
            rs.getString("status")
    );

    private static final RowMapper<ProductPriceRow> PRODUCT_PRICE_MAPPER = (rs, rowNum) -> new ProductPriceRow(
            rs.getLong("product_id"),
            rs.getBigDecimal("retail_price"),
            rs.getBigDecimal("lowest_price"),
            rs.getBigDecimal("buying_price")
    );

    private static Long getLongOrNull(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private static String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static String jsonArray(List<String> values) {
        return "[" + String.join(",", values.stream().map(value -> "\"" + value + "\"").toList()) + "]";
    }

    private static List<String> jsonArrayToList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        return List.of(json.replace("[", "").replace("]", "").replace("\"", "").split(","))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public record ProductScopeQuery(
            int companyId,
            int branchId,
            String queryText,
            List<Long> productIds,
            String category,
            String major,
            String businessLineKey,
            String templateKey,
            Integer supplierId,
            int maxProducts
    ) {
    }

    public record AdjustmentProductRow(
            long productId,
            Long recommendationItemId,
            String productName,
            BigDecimal retailPrice,
            BigDecimal lowestPrice,
            BigDecimal buyingPrice,
            BigDecimal suggestedRetailPrice,
            BigDecimal suggestedLowestPrice
    ) {
    }

    public record AdjustmentItemDraft(
            long productId,
            Long recommendationItemId,
            String productName,
            BigDecimal oldRetailPrice,
            BigDecimal newRetailPrice,
            BigDecimal oldLowestPrice,
            BigDecimal newLowestPrice,
            BigDecimal buyingPriceSnapshot,
            BigDecimal deltaAmount,
            BigDecimal deltaPct,
            BigDecimal expectedMarginPct,
            String status,
            List<String> reasonCodes,
            List<String> warningCodes,
            List<String> blockedCodes
    ) {
    }

    public record ItemsPage(int page, int size, long totalItems, int totalPages, List<PriceAdjustmentItemPreviewResponse> items) {
    }

    public record BatchesPage(int page, int size, long totalItems, int totalPages, List<PriceAdjustmentBatchResponse> items) {
    }

    public record ApplyItemRow(
            long itemId,
            long batchId,
            long productId,
            Long recommendationItemId,
            String productName,
            BigDecimal oldRetailPrice,
            BigDecimal newRetailPrice,
            BigDecimal oldLowestPrice,
            BigDecimal newLowestPrice,
            BigDecimal buyingPriceSnapshot,
            BigDecimal deltaAmount,
            BigDecimal deltaPct,
            String status
    ) {
    }

    public record ProductPriceRow(
            long productId,
            BigDecimal retailPrice,
            BigDecimal lowestPrice,
            BigDecimal buyingPrice
    ) {
    }
}
