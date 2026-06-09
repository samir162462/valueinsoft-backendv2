package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationItemResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationRunResponse;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceReasonCode;
import com.example.valueinsoftbackend.pricing.dynamic.model.PricingRecommendation;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.math.BigDecimal;

@Repository
public class PriceRecommendationRunRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PriceRecommendationRunRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createRun(int companyId, int branchId, int metricsWindowDays, String actorName,
                          String scopeJson, String policySnapshotJson) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, status, scope_json, policy_snapshot_json,
                    metrics_window_days, created_by, started_at
                ) VALUES (
                    :companyId, :branchId, 'RUNNING', CAST(:scopeJson AS jsonb), CAST(:policySnapshotJson AS jsonb),
                    :metricsWindowDays, :actorName, NOW()
                )
                RETURNING run_id
                """.formatted(TenantSqlIdentifiers.inventoryPriceRecommendationRunTable(companyId));

        Long id = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("scopeJson", scopeJson)
                .addValue("policySnapshotJson", policySnapshotJson)
                .addValue("metricsWindowDays", metricsWindowDays)
                .addValue("actorName", actorName), Long.class);
        return id == null ? 0L : id;
    }

    public void insertItem(int companyId, int branchId, long runId, PricingRecommendation recommendation) {
        var metrics = recommendation.metrics();
        String sql = """
                INSERT INTO %s (
                    run_id, company_id, branch_id, product_id, product_name, category, pricing_policy_code,
                    old_retail_price, old_lowest_price, buying_price, suggested_retail_price, suggested_lowest_price,
                    delta_amount, delta_pct, current_margin_pct, suggested_margin_pct, stock_qty,
                    sales_velocity_7d, sales_velocity_30d, sales_velocity_90d, days_cover, movement_class,
                    demand_score, cost_change_pct, recommendation_status, approval_required,
                    reason_codes, explanation_json, warning_codes, created_at
                ) VALUES (
                    :runId, :companyId, :branchId, :productId, :productName, :category, :pricingPolicyCode,
                    :oldRetailPrice, :oldLowestPrice, :buyingPrice, :suggestedRetailPrice, :suggestedLowestPrice,
                    :deltaAmount, :deltaPct, :currentMarginPct, :suggestedMarginPct, :stockQty,
                    :salesVelocity7d, :salesVelocity30d, :salesVelocity90d, :daysCover, :movementClass,
                    :demandScore, :costChangePct, :recommendationStatus, :approvalRequired,
                    CAST(:reasonCodes AS jsonb), CAST(:explanationJson AS jsonb), CAST(:warningCodes AS jsonb), NOW()
                )
                """.formatted(TenantSqlIdentifiers.inventoryPriceRecommendationItemTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productId", metrics.productId())
                .addValue("productName", metrics.productName())
                .addValue("category", metrics.category())
                .addValue("pricingPolicyCode", metrics.pricingPolicyCode())
                .addValue("oldRetailPrice", metrics.retailPrice())
                .addValue("oldLowestPrice", metrics.lowestPrice())
                .addValue("buyingPrice", metrics.buyingPrice())
                .addValue("suggestedRetailPrice", recommendation.suggestedRetailPrice())
                .addValue("suggestedLowestPrice", recommendation.suggestedLowestPrice())
                .addValue("deltaAmount", recommendation.deltaAmount())
                .addValue("deltaPct", recommendation.deltaPct())
                .addValue("currentMarginPct", metrics.currentMarginPct())
                .addValue("suggestedMarginPct", recommendation.suggestedMarginPct())
                .addValue("stockQty", metrics.stockQty())
                .addValue("salesVelocity7d", metrics.salesVelocity7d())
                .addValue("salesVelocity30d", metrics.salesVelocity30d())
                .addValue("salesVelocity90d", metrics.salesVelocity90d())
                .addValue("daysCover", metrics.daysCover())
                .addValue("movementClass", metrics.movementClass().name())
                .addValue("demandScore", metrics.demandScore())
                .addValue("costChangePct", metrics.costChangePct())
                .addValue("recommendationStatus", recommendation.status().name())
                .addValue("approvalRequired", recommendation.approvalRequired())
                .addValue("reasonCodes", jsonArray(recommendation.reasonCodes()))
                .addValue("explanationJson", recommendation.explanationJson())
                .addValue("warningCodes", jsonArray(recommendation.warningCodes())));
    }

    public void completeRun(int companyId, long runId, int total, int recommended, int warnings, int skipped) {
        String status = warnings > 0 ? "COMPLETED_WITH_WARNINGS" : "COMPLETED";
        String sql = """
                UPDATE %s
                SET status = :status,
                    total_products = :total,
                    recommended_products = :recommended,
                    warning_products = :warnings,
                    skipped_products = :skipped,
                    completed_at = NOW()
                WHERE run_id = :runId
                """.formatted(TenantSqlIdentifiers.inventoryPriceRecommendationRunTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("total", total)
                .addValue("recommended", recommended)
                .addValue("warnings", warnings)
                .addValue("skipped", skipped)
                .addValue("runId", runId));
    }

    public void failRun(int companyId, long runId, String failureReason) {
        String sql = """
                UPDATE %s
                SET status = 'FAILED',
                    failure_reason = :failureReason,
                    completed_at = NOW()
                WHERE run_id = :runId
                """.formatted(TenantSqlIdentifiers.inventoryPriceRecommendationRunTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("failureReason", failureReason)
                .addValue("runId", runId));
    }

    public PriceRecommendationRunResponse findRun(int companyId, long runId) {
        String sql = """
                SELECT *
                FROM %s
                WHERE run_id = :runId
                """.formatted(TenantSqlIdentifiers.inventoryPriceRecommendationRunTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource().addValue("runId", runId), RUN_MAPPER);
    }

    public ItemsPage findItems(int companyId, int branchId, long runId, String status, int page, int size, boolean includeCost) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int offset = safePage * safeSize;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("branchId", branchId)
                .addValue("status", status)
                .addValue("limit", safeSize)
                .addValue("offset", offset);

        String whereStatus = status == null || status.isBlank() ? "" : " AND recommendation_status = :status ";
        String table = TenantSqlIdentifiers.inventoryPriceRecommendationItemTable(companyId);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE run_id = :runId AND branch_id = :branchId " + whereStatus,
                params,
                Long.class
        );
        List<PriceRecommendationItemResponse> items = jdbcTemplate.query(
                "SELECT * FROM " + table + " WHERE run_id = :runId AND branch_id = :branchId " + whereStatus +
                        " ORDER BY product_name ASC, product_id ASC LIMIT :limit OFFSET :offset",
                params,
                itemMapper(includeCost)
        );
        long totalItems = total == null ? 0 : total;
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / safeSize);
        return new ItemsPage(safePage, safeSize, totalItems, totalPages, items);
    }

    public PriceRecommendationItemResponse findItem(int companyId, int branchId, long itemId) {
        String sql = "SELECT * FROM %s WHERE item_id = :itemId AND branch_id = :branchId"
                .formatted(TenantSqlIdentifiers.inventoryPriceRecommendationItemTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("branchId", branchId), itemMapper(true));
    }

    public void updateSuggestedPrice(int companyId, int branchId, long itemId, BigDecimal suggestedPrice, BigDecimal deltaAmount, BigDecimal deltaPct, BigDecimal suggestedMarginPct) {
        String sql = """
                UPDATE %s
                SET suggested_retail_price = :suggestedPrice,
                    suggested_lowest_price = LEAST(old_lowest_price, :suggestedPrice),
                    delta_amount = :deltaAmount,
                    delta_pct = :deltaPct,
                    suggested_margin_pct = :suggestedMarginPct,
                    recommendation_status = 'RECOMMENDED'
                WHERE item_id = :itemId
                  AND branch_id = :branchId
                """.formatted(TenantSqlIdentifiers.inventoryPriceRecommendationItemTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("suggestedPrice", suggestedPrice)
                .addValue("deltaAmount", deltaAmount)
                .addValue("deltaPct", deltaPct)
                .addValue("suggestedMarginPct", suggestedMarginPct)
                .addValue("itemId", itemId)
                .addValue("branchId", branchId));
    }

    public void bulkRoundSuggestedPrices(int companyId, int branchId, long runId, BigDecimal factor) {
        String table = TenantSqlIdentifiers.inventoryPriceRecommendationItemTable(companyId);
        String sql = """
                UPDATE %s
                SET suggested_retail_price = ROUND(suggested_retail_price / :factor) * :factor,
                    suggested_lowest_price = LEAST(old_lowest_price, ROUND(suggested_retail_price / :factor) * :factor),
                    delta_amount = (ROUND(suggested_retail_price / :factor) * :factor) - old_retail_price,
                    delta_pct = CASE WHEN old_retail_price > 0 THEN ((ROUND(suggested_retail_price / :factor) * :factor) - old_retail_price) / old_retail_price ELSE 0 END,
                    suggested_margin_pct = CASE WHEN (ROUND(suggested_retail_price / :factor) * :factor) > 0 AND buying_price IS NOT NULL THEN ((ROUND(suggested_retail_price / :factor) * :factor) - buying_price) / (ROUND(suggested_retail_price / :factor) * :factor) ELSE suggested_margin_pct END,
                    recommendation_status = 'RECOMMENDED'
                WHERE run_id = :runId
                  AND branch_id = :branchId
                  AND recommendation_status IN ('RECOMMENDED', 'WARNING')
                  AND suggested_retail_price IS NOT NULL
                """.formatted(table);

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("factor", factor)
                .addValue("runId", runId)
                .addValue("branchId", branchId));
    }



    private static final RowMapper<PriceRecommendationRunResponse> RUN_MAPPER = (rs, rowNum) -> new PriceRecommendationRunResponse(
            rs.getLong("run_id"),
            rs.getInt("company_id"),
            rs.getInt("branch_id"),
            rs.getString("status"),
            rs.getInt("metrics_window_days"),
            rs.getInt("total_products"),
            rs.getInt("recommended_products"),
            rs.getInt("warning_products"),
            rs.getInt("skipped_products"),
            rs.getString("created_by"),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class),
            rs.getString("failure_reason")
    );

    private static RowMapper<PriceRecommendationItemResponse> itemMapper(boolean includeCost) {
        return (rs, rowNum) -> new PriceRecommendationItemResponse(
                rs.getLong("item_id"),
                rs.getLong("run_id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("category"),
                rs.getString("pricing_policy_code"),
                rs.getBigDecimal("old_retail_price"),
                rs.getBigDecimal("old_lowest_price"),
                includeCost ? rs.getBigDecimal("buying_price") : null,
                rs.getBigDecimal("suggested_retail_price"),
                rs.getBigDecimal("suggested_lowest_price"),
                rs.getBigDecimal("delta_amount"),
                rs.getBigDecimal("delta_pct"),
                includeCost ? rs.getBigDecimal("current_margin_pct") : null,
                includeCost ? rs.getBigDecimal("suggested_margin_pct") : null,
                rs.getBigDecimal("stock_qty"),
                rs.getBigDecimal("sales_velocity_7d"),
                rs.getBigDecimal("sales_velocity_30d"),
                rs.getBigDecimal("sales_velocity_90d"),
                rs.getBigDecimal("days_cover"),
                rs.getString("movement_class"),
                rs.getBigDecimal("demand_score"),
                includeCost ? rs.getBigDecimal("cost_change_pct") : null,
                rs.getString("recommendation_status"),
                rs.getBoolean("approval_required"),
                jsonArrayToList(rs.getString("reason_codes")),
                rs.getString("explanation_json"),
                jsonArrayToList(rs.getString("warning_codes")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private static String jsonArray(List<PriceReasonCode> codes) {
        return "[" + String.join(",", codes.stream().map(code -> "\"" + code.name() + "\"").toList()) + "]";
    }

    private static List<String> jsonArrayToList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        return List.of(json.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split(","))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public record ItemsPage(int page, int size, long totalItems, int totalPages, List<PriceRecommendationItemResponse> items) {
    }
}
