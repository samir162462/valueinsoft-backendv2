package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.pricing.dynamic.dto.DynamicPricingPolicyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.model.ProductPricingScope;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DynamicPricingPolicyRepository {

    private static final RowMapper<DynamicPricingPolicy> POLICY_MAPPER = new RowMapper<>() {
        @Override
        public DynamicPricingPolicy mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DynamicPricingPolicy(
                    rs.getLong("policy_id"),
                    rs.getLong("company_id"),
                    getLongOrNull(rs, "branch_id"),
                    rs.getString("scope_type"),
                    rs.getString("scope_value"),
                    rs.getString("display_name"),
                    rs.getBigDecimal("target_margin_pct"),
                    rs.getBigDecimal("min_margin_pct"),
                    rs.getBigDecimal("max_increase_pct"),
                    rs.getBigDecimal("max_decrease_pct"),
                    rs.getBigDecimal("max_increase_amount"),
                    rs.getBigDecimal("max_decrease_amount"),
                    rs.getBigDecimal("min_final_price"),
                    rs.getBigDecimal("max_final_price"),
                    rs.getBoolean("allow_below_cost"),
                    rs.getBoolean("approval_required"),
                    rs.getBoolean("maker_checker_required"),
                    rs.getBoolean("auto_apply_allowed"),
                    rs.getBigDecimal("max_auto_apply_pct"),
                    rs.getInt("max_products_per_batch"),
                    rs.getString("rounding_mode"),
                    rs.getBigDecimal("low_stock_days_cover"),
                    rs.getBigDecimal("overstock_days_cover"),
                    rs.getInt("slow_moving_days"),
                    rs.getInt("dead_stock_days"),
                    rs.getString("config_json"),
                    rs.getBoolean("is_active"),
                    rs.getString("created_by"),
                    rs.getString("updated_by"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)
            );
        }
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DynamicPricingPolicyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DynamicPricingPolicy> findEffectivePolicy(int companyId, int branchId, Long productId) {
        ProductPricingScope productScope = productId == null ? null : findProductScope(companyId, productId).orElse(null);
        String table = TenantSqlIdentifiers.inventoryDynamicPricingPolicyTable(companyId);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productIdText", productId == null ? null : String.valueOf(productId))
                .addValue("category", normalize(productScope == null ? null : productScope.category()))
                .addValue("businessLineKey", normalize(productScope == null ? null : productScope.businessLineKey()))
                .addValue("pricingPolicyCode", normalize(productScope == null ? null : productScope.pricingPolicyCode()));

        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND is_active = TRUE
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND (
                        (scope_type = 'PRODUCT' AND CAST(scope_value AS TEXT) = CAST(:productIdText AS TEXT))
                     OR (scope_type = 'CATEGORY' AND LOWER(scope_value) = :category)
                     OR (scope_type = 'BUSINESS_LINE' AND LOWER(scope_value) = :businessLineKey)
                     OR (scope_type = 'PRICING_POLICY' AND LOWER(scope_value) = :pricingPolicyCode)
                     OR (scope_type = 'BRANCH' AND branch_id = :branchId)
                     OR (scope_type = 'COMPANY')
                  )
                ORDER BY
                    CASE
                        WHEN scope_type = 'PRODUCT' THEN 1
                        WHEN scope_type = 'CATEGORY' THEN 2
                        WHEN scope_type = 'PRICING_POLICY' THEN 3
                        WHEN scope_type = 'BUSINESS_LINE' THEN 4
                        WHEN scope_type = 'BRANCH' THEN 5
                        WHEN scope_type = 'COMPANY' THEN 6
                        ELSE 99
                    END ASC,
                    CASE WHEN branch_id = :branchId THEN 0 ELSE 1 END ASC,
                    updated_at DESC
                LIMIT 1
                """.formatted(table);

        List<DynamicPricingPolicy> policies = jdbcTemplate.query(sql, params, POLICY_MAPPER);
        return policies.stream().findFirst();
    }

    public DynamicPricingPolicy save(String actorName, DynamicPricingPolicyRequest request) {
        if (request.policyId() == null) {
            return insert(actorName, request);
        }
        return update(actorName, request);
    }

    public Optional<ProductPricingScope> findProductScope(int companyId, long productId) {
        String sql = """
                SELECT product_id, major, business_line_key, pricing_policy_code
                FROM %s
                WHERE product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        List<ProductPricingScope> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("productId", productId),
                (rs, rowNum) -> new ProductPricingScope(
                        rs.getLong("product_id"),
                        rs.getString("major"),
                        rs.getString("business_line_key"),
                        rs.getString("pricing_policy_code")
                )
        );
        return rows.stream().findFirst();
    }

    private DynamicPricingPolicy insert(String actorName, DynamicPricingPolicyRequest request) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, scope_type, scope_value, display_name,
                    target_margin_pct, min_margin_pct, max_increase_pct, max_decrease_pct,
                    max_increase_amount, max_decrease_amount, min_final_price, max_final_price,
                    allow_below_cost, approval_required, maker_checker_required, auto_apply_allowed,
                    max_auto_apply_pct, max_products_per_batch, rounding_mode, low_stock_days_cover,
                    overstock_days_cover, slow_moving_days, dead_stock_days, config_json, is_active,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (
                    :companyId, :branchId, :scopeType, :scopeValue, :displayName,
                    :targetMarginPct, :minMarginPct, :maxIncreasePct, :maxDecreasePct,
                    :maxIncreaseAmount, :maxDecreaseAmount, :minFinalPrice, :maxFinalPrice,
                    :allowBelowCost, :approvalRequired, :makerCheckerRequired, :autoApplyAllowed,
                    :maxAutoApplyPct, :maxProductsPerBatch, :roundingMode, :lowStockDaysCover,
                    :overstockDaysCover, :slowMovingDays, :deadStockDays, CAST(:configJson AS jsonb), :active,
                    :actorName, :actorName, NOW(), NOW()
                )
                RETURNING *
                """.formatted(TenantSqlIdentifiers.inventoryDynamicPricingPolicyTable(request.companyId()));

        return jdbcTemplate.queryForObject(sql, buildParams(actorName, request), POLICY_MAPPER);
    }

    private DynamicPricingPolicy update(String actorName, DynamicPricingPolicyRequest request) {
        String sql = """
                UPDATE %s
                SET branch_id = :branchId,
                    scope_type = :scopeType,
                    scope_value = :scopeValue,
                    display_name = :displayName,
                    target_margin_pct = :targetMarginPct,
                    min_margin_pct = :minMarginPct,
                    max_increase_pct = :maxIncreasePct,
                    max_decrease_pct = :maxDecreasePct,
                    max_increase_amount = :maxIncreaseAmount,
                    max_decrease_amount = :maxDecreaseAmount,
                    min_final_price = :minFinalPrice,
                    max_final_price = :maxFinalPrice,
                    allow_below_cost = :allowBelowCost,
                    approval_required = :approvalRequired,
                    maker_checker_required = :makerCheckerRequired,
                    auto_apply_allowed = :autoApplyAllowed,
                    max_auto_apply_pct = :maxAutoApplyPct,
                    max_products_per_batch = :maxProductsPerBatch,
                    rounding_mode = :roundingMode,
                    low_stock_days_cover = :lowStockDaysCover,
                    overstock_days_cover = :overstockDaysCover,
                    slow_moving_days = :slowMovingDays,
                    dead_stock_days = :deadStockDays,
                    config_json = CAST(:configJson AS jsonb),
                    is_active = :active,
                    updated_by = :actorName,
                    updated_at = NOW()
                WHERE policy_id = :policyId
                  AND company_id = :companyId
                RETURNING *
                """.formatted(TenantSqlIdentifiers.inventoryDynamicPricingPolicyTable(request.companyId()));

        List<DynamicPricingPolicy> rows = jdbcTemplate.query(sql, buildParams(actorName, request), POLICY_MAPPER);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Pricing policy was not found");
        }
        return rows.getFirst();
    }

    private MapSqlParameterSource buildParams(String actorName, DynamicPricingPolicyRequest request) {
        return new MapSqlParameterSource()
                .addValue("policyId", request.policyId())
                .addValue("companyId", request.companyId())
                .addValue("branchId", request.branchId())
                .addValue("scopeType", request.scopeType())
                .addValue("scopeValue", blankToNull(request.scopeValue()))
                .addValue("displayName", request.displayName())
                .addValue("targetMarginPct", request.targetMarginPct())
                .addValue("minMarginPct", request.minMarginPct())
                .addValue("maxIncreasePct", request.maxIncreasePct())
                .addValue("maxDecreasePct", request.maxDecreasePct())
                .addValue("maxIncreaseAmount", request.maxIncreaseAmount())
                .addValue("maxDecreaseAmount", request.maxDecreaseAmount())
                .addValue("minFinalPrice", request.minFinalPrice())
                .addValue("maxFinalPrice", request.maxFinalPrice())
                .addValue("allowBelowCost", Boolean.TRUE.equals(request.allowBelowCost()))
                .addValue("approvalRequired", request.approvalRequired() == null || request.approvalRequired())
                .addValue("makerCheckerRequired", request.makerCheckerRequired() == null || request.makerCheckerRequired())
                .addValue("autoApplyAllowed", Boolean.TRUE.equals(request.autoApplyAllowed()))
                .addValue("maxAutoApplyPct", request.maxAutoApplyPct())
                .addValue("maxProductsPerBatch", request.maxProductsPerBatch() == null ? 500 : request.maxProductsPerBatch())
                .addValue("roundingMode", blankToDefault(request.roundingMode(), "NEAREST_1"))
                .addValue("lowStockDaysCover", defaultValue(request.lowStockDaysCover(), new BigDecimal("7.0000")))
                .addValue("overstockDaysCover", defaultValue(request.overstockDaysCover(), new BigDecimal("60.0000")))
                .addValue("slowMovingDays", request.slowMovingDays() == null ? 45 : request.slowMovingDays())
                .addValue("deadStockDays", request.deadStockDays() == null ? 120 : request.deadStockDays())
                .addValue("configJson", blankToDefault(request.configJson(), "{}"))
                .addValue("active", request.active() == null || request.active())
                .addValue("actorName", actorName);
    }

    private static DynamicPricingPolicy defaultValue(long companyId, int branchId) {
        return new DynamicPricingPolicy(
                null,
                companyId,
                (long) branchId,
                "SYSTEM_DEFAULT",
                null,
                "System default pricing guardrails",
                new BigDecimal("0.2000"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.0500"),
                new BigDecimal("0.1000"),
                null,
                null,
                null,
                null,
                false,
                true,
                true,
                false,
                null,
                500,
                "NEAREST_1",
                new BigDecimal("7.0000"),
                new BigDecimal("60.0000"),
                45,
                120,
                "{}",
                true,
                "system",
                null,
                null,
                null
        );
    }

    public DynamicPricingPolicy systemDefaultPolicy(int companyId, int branchId) {
        return defaultValue(companyId, branchId);
    }

    private static Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static BigDecimal defaultValue(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }
}
