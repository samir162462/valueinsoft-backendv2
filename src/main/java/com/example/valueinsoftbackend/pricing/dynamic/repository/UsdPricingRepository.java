package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingProductRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class UsdPricingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UsdPricingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProductsPage findProducts(UsdPricingProductRequest request) {
        int page = Math.max(0, request.page() == null ? 0 : request.page());
        int size = Math.min(Math.max(1, request.size() == null ? 25 : request.size()), 100);
        int offset = page * size;

        MapSqlParameterSource params = params(request)
                .addValue("limit", size)
                .addValue("offset", offset);
        String where = whereClause(request);
        String from = fromClause(request.companyId(), request.branchId());

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from + where, params, Long.class);
        List<ProductRow> items = jdbcTemplate.query(
                """
                SELECT
                    p.product_id,
                    p.product_name,
                    COALESCE(ibp.category_name, p.major) AS category,
                    p.business_line_key,
                    p.template_key,
                    p.pricing_policy_code,
                    p.supplier_id,
                    p.serial,
                    p.barcode,
                    COALESCE(stock.quantity, 0)::numeric AS stock_qty,
                    COALESCE(p.fx_pricing_enabled, FALSE) AS fx_pricing_enabled,
                    p.replacement_cost_usd,
                    p.replacement_cost_currency,
                    p.purchase_usd_rate,
                    p.replacement_cost_updated_at,
                    rate.global_fx_snapshot_id,
                    rate.global_rate,
                    rate.effective_pricing_rate,
                    rate.safety_buffer_percentage,
                    rate.selected_rate_type,
                    rate.effective_date,
                    rate.calculation_timestamp,
                    p.buying_price::numeric AS current_buying_price,
                    p.retail_price::numeric AS current_retail_price,
                    p.lowest_price::numeric AS current_lowest_price
                """ + from + where + """
                ORDER BY p.product_name ASC, p.product_id ASC
                LIMIT :limit OFFSET :offset
                """,
                params,
                PRODUCT_ROW_MAPPER
        );

        long totalItems = total == null ? 0L : total;
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / (double) size);
        return new ProductsPage(page, size, totalItems, totalPages, items);
    }

    public void ensureLatestCompanyRate(int companyId) {
        jdbcTemplate.update(
                """
                WITH latest_snapshot AS (
                    SELECT id, rate
                    FROM public.global_fx_rate_snapshot
                    WHERE base_currency = 'USD'
                      AND target_currency = 'EGP'
                      AND status = 'VALID'
                      AND validation_status = 'VALID'
                      AND rate IS NOT NULL
                      AND rate > 0
                      AND effective_date IS NOT NULL
                    ORDER BY effective_date DESC,
                             request_timestamp DESC NULLS LAST,
                             id DESC
                    LIMIT 1
                ),
                cfg AS (
                    INSERT INTO public.company_fx_pricing_config (
                        company_id,
                        fx_pricing_enabled,
                        safety_buffer_percentage,
                        selected_rate_type,
                        margin_rules_json,
                        rounding_rules_json,
                        repricing_threshold_json,
                        config_version,
                        updated_by
                    ) VALUES (
                        :companyId,
                        TRUE,
                        0,
                        'REFERENCE',
                        '{}'::jsonb,
                        '{}'::jsonb,
                        '{}'::jsonb,
                        1,
                        'system_pricing_usd'
                    )
                    ON CONFLICT (company_id) DO UPDATE
                    SET updated_at = public.company_fx_pricing_config.updated_at
                    RETURNING company_id, safety_buffer_percentage, selected_rate_type, config_version
                )
                INSERT INTO public.company_fx_effective_rate (
                    company_id,
                    global_fx_snapshot_id,
                    safety_buffer_percentage,
                    effective_pricing_rate,
                    selected_rate_type,
                    config_version,
                    status,
                    calculation_timestamp
                )
                SELECT
                    cfg.company_id,
                    latest_snapshot.id,
                    cfg.safety_buffer_percentage,
                    (latest_snapshot.rate * (1 + (cfg.safety_buffer_percentage / 100.0)))::numeric(19,8),
                    cfg.selected_rate_type,
                    cfg.config_version,
                    'VALID',
                    NOW()
                FROM cfg
                JOIN latest_snapshot ON TRUE
                ON CONFLICT (company_id, global_fx_snapshot_id) DO UPDATE
                SET safety_buffer_percentage = EXCLUDED.safety_buffer_percentage,
                    effective_pricing_rate = EXCLUDED.effective_pricing_rate,
                    selected_rate_type = EXCLUDED.selected_rate_type,
                    config_version = EXCLUDED.config_version,
                    status = 'VALID',
                    calculation_timestamp = NOW(),
                    updated_at = NOW()
                """,
                new MapSqlParameterSource().addValue("companyId", companyId)
        );
    }

    public int backfillMissingUsdCosts(int companyId) {
        String sql = """
                WITH rate AS (
                    SELECT cer.effective_pricing_rate
                    FROM public.company_fx_effective_rate cer
                    JOIN public.global_fx_rate_snapshot snapshot
                      ON snapshot.id = cer.global_fx_snapshot_id
                    WHERE cer.company_id = :companyId
                      AND cer.status = 'VALID'
                      AND snapshot.status = 'VALID'
                      AND snapshot.validation_status = 'VALID'
                      AND cer.effective_pricing_rate > 0
                    ORDER BY snapshot.effective_date DESC NULLS LAST,
                             snapshot.request_timestamp DESC NULLS LAST,
                             snapshot.id DESC
                    LIMIT 1
                )
                UPDATE %s p
                SET fx_pricing_enabled = TRUE,
                    replacement_cost_usd = CASE
                        WHEN p.replacement_cost_usd IS NULL OR p.replacement_cost_usd <= 0
                            THEN ROUND((p.buying_price::numeric / rate.effective_pricing_rate), 4)
                        ELSE p.replacement_cost_usd
                    END,
                    replacement_cost_currency = 'USD',
                    purchase_usd_rate = CASE
                        WHEN p.replacement_cost_usd IS NOT NULL AND p.replacement_cost_usd > 0
                            THEN ROUND((p.buying_price::numeric / p.replacement_cost_usd), 8)
                        ELSE rate.effective_pricing_rate
                    END,
                    replacement_cost_updated_at = NOW(),
                    updated_at = NOW()
                FROM rate
                WHERE p.company_id = :companyId
                  AND p.buying_price IS NOT NULL
                  AND p.buying_price > 0
                  AND (
                      COALESCE(p.fx_pricing_enabled, FALSE) = FALSE
                      OR p.replacement_cost_usd IS NULL
                      OR p.replacement_cost_usd <= 0
                      OR p.replacement_cost_currency IS DISTINCT FROM 'USD'
                      OR p.purchase_usd_rate IS NULL
                      OR p.purchase_usd_rate <= 0
                  )
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        return jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("companyId", companyId));
    }

    public Optional<RateRow> findLatestCompanyRate(int companyId) {
        List<RateRow> rows = jdbcTemplate.query(
                """
                SELECT
                    cer.global_fx_snapshot_id,
                    snapshot.rate AS global_rate,
                    cer.effective_pricing_rate,
                    cer.safety_buffer_percentage,
                    cer.selected_rate_type,
                    snapshot.effective_date,
                    cer.calculation_timestamp
                FROM public.company_fx_effective_rate cer
                JOIN public.global_fx_rate_snapshot snapshot
                  ON snapshot.id = cer.global_fx_snapshot_id
                WHERE cer.company_id = :companyId
                  AND cer.status = 'VALID'
                  AND snapshot.status = 'VALID'
                  AND snapshot.validation_status = 'VALID'
                ORDER BY snapshot.effective_date DESC NULLS LAST,
                         snapshot.request_timestamp DESC NULLS LAST,
                         snapshot.id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource().addValue("companyId", companyId),
                RATE_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public Optional<ProductRow> findProduct(int companyId, int branchId, long productId) {
        String from = fromClause(companyId, branchId);
        List<ProductRow> rows = jdbcTemplate.query(
                """
                SELECT
                    p.product_id,
                    p.product_name,
                    COALESCE(ibp.category_name, p.major) AS category,
                    p.business_line_key,
                    p.template_key,
                    p.pricing_policy_code,
                    p.supplier_id,
                    p.serial,
                    p.barcode,
                    COALESCE(stock.quantity, 0)::numeric AS stock_qty,
                    COALESCE(p.fx_pricing_enabled, FALSE) AS fx_pricing_enabled,
                    p.replacement_cost_usd,
                    p.replacement_cost_currency,
                    p.purchase_usd_rate,
                    p.replacement_cost_updated_at,
                    rate.global_fx_snapshot_id,
                    rate.global_rate,
                    rate.effective_pricing_rate,
                    rate.safety_buffer_percentage,
                    rate.selected_rate_type,
                    rate.effective_date,
                    rate.calculation_timestamp,
                    p.buying_price::numeric AS current_buying_price,
                    p.retail_price::numeric AS current_retail_price,
                    p.lowest_price::numeric AS current_lowest_price
                """ + from + " WHERE p.company_id = :companyId AND p.product_id = :productId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productId", productId),
                PRODUCT_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public Optional<ProductCostRow> findProductCost(int companyId, long productId) {
        String sql = """
                SELECT product_id, product_name, COALESCE(fx_pricing_enabled, FALSE) AS fx_pricing_enabled,
                       replacement_cost_usd, replacement_cost_currency, purchase_usd_rate, replacement_cost_updated_at
                FROM %s
                WHERE product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        List<ProductCostRow> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("productId", productId),
                PRODUCT_COST_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public void updateUsdCost(int companyId, long productId, boolean enabled, BigDecimal replacementCostUsd, BigDecimal purchaseUsdRate) {
        String sql = """
                UPDATE %s
                SET fx_pricing_enabled = :enabled,
                    replacement_cost_usd = :replacementCostUsd,
                    replacement_cost_currency = 'USD',
                    purchase_usd_rate = :purchaseUsdRate,
                    replacement_cost_updated_at = CASE
                        WHEN :enabled = TRUE THEN NOW()
                        ELSE replacement_cost_updated_at
                    END,
                    updated_at = NOW()
                WHERE product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("enabled", enabled)
                        .addValue("replacementCostUsd", replacementCostUsd)
                        .addValue("purchaseUsdRate", purchaseUsdRate)
                        .addValue("productId", productId)
        );
    }

    private String fromClause(int companyId, int branchId) {
        return """
                FROM %s p
                INNER JOIN %s ibp
                  ON ibp.product_id = p.product_id
                 AND ibp.branch_id = :branchId
                 AND ibp.is_active = TRUE
                LEFT JOIN %s stock
                  ON stock.product_id = p.product_id
                 AND stock.branch_id = :branchId
                LEFT JOIN (
                    SELECT
                        cer.global_fx_snapshot_id,
                        snapshot.rate AS global_rate,
                        cer.effective_pricing_rate,
                        cer.safety_buffer_percentage,
                        cer.selected_rate_type,
                        snapshot.effective_date,
                        cer.calculation_timestamp
                    FROM public.company_fx_effective_rate cer
                    JOIN public.global_fx_rate_snapshot snapshot
                      ON snapshot.id = cer.global_fx_snapshot_id
                    WHERE cer.company_id = :companyId
                      AND cer.status = 'VALID'
                      AND snapshot.status = 'VALID'
                      AND snapshot.validation_status = 'VALID'
                    ORDER BY snapshot.effective_date DESC NULLS LAST,
                             snapshot.request_timestamp DESC NULLS LAST,
                             snapshot.id DESC
                    LIMIT 1
                ) rate ON TRUE
                """.formatted(
                TenantSqlIdentifiers.inventoryProductTable(companyId),
                TenantSqlIdentifiers.inventoryBranchProductTable(companyId),
                TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId)
        );
    }

    private String whereClause(UsdPricingProductRequest request) {
        StringBuilder where = new StringBuilder(" WHERE p.company_id = :companyId ");
        if (request.query() != null && !request.query().isBlank()) {
            where.append("""
                    AND (
                        p.product_name ILIKE :query
                        OR p.serial ILIKE :query
                        OR p.barcode ILIKE :query
                    )
                    """);
        }
        if (request.category() != null && !request.category().isBlank()) {
            where.append(" AND (LOWER(COALESCE(ibp.group_name, '')) = LOWER(:category) OR LOWER(COALESCE(ibp.category_name, p.major, '')) = LOWER(:category) OR LOWER(COALESCE(ibp.subcategory_name, p.product_type, '')) = LOWER(:category) OR LOWER(COALESCE(ibp.group_key, '')) = LOWER(:category) OR LOWER(COALESCE(ibp.category_key, '')) = LOWER(:category) OR LOWER(COALESCE(ibp.subcategory_key, '')) = LOWER(:category)) ");
        }
        if (request.businessLineKey() != null && !request.businessLineKey().isBlank()) {
            where.append(" AND p.business_line_key = :businessLineKey ");
        }
        if (request.templateKey() != null && !request.templateKey().isBlank()) {
            where.append(" AND p.template_key = :templateKey ");
        }
        if (request.supplierId() != null && request.supplierId() > 0) {
            where.append(" AND p.supplier_id = :supplierId ");
        }
        if (Boolean.TRUE.equals(request.fxOnly())) {
            where.append(" AND COALESCE(p.fx_pricing_enabled, FALSE) = TRUE ");
        }
        return where.toString();
    }

    private MapSqlParameterSource params(UsdPricingProductRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", request.companyId())
                .addValue("branchId", request.branchId());
        if (request.query() != null && !request.query().isBlank()) {
            params.addValue("query", "%" + request.query().trim() + "%");
        }
        params.addValue("category", trimToNull(request.category()));
        params.addValue("businessLineKey", trimToNull(request.businessLineKey()));
        params.addValue("templateKey", trimToNull(request.templateKey()));
        params.addValue("supplierId", request.supplierId());
        return params;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final RowMapper<ProductRow> PRODUCT_ROW_MAPPER = (rs, rowNum) -> new ProductRow(
            rs.getLong("product_id"),
            rs.getString("product_name"),
            rs.getString("category"),
            rs.getString("business_line_key"),
            rs.getString("template_key"),
            rs.getString("pricing_policy_code"),
            nullableInt(rs.getObject("supplier_id")),
            rs.getString("serial"),
            rs.getString("barcode"),
            rs.getBigDecimal("stock_qty"),
            rs.getBoolean("fx_pricing_enabled"),
            rs.getBigDecimal("replacement_cost_usd"),
            rs.getString("replacement_cost_currency"),
            rs.getBigDecimal("purchase_usd_rate"),
            rs.getObject("replacement_cost_updated_at", OffsetDateTime.class),
            nullableLong(rs.getObject("global_fx_snapshot_id")),
            rs.getBigDecimal("global_rate"),
            rs.getBigDecimal("effective_pricing_rate"),
            rs.getBigDecimal("safety_buffer_percentage"),
            rs.getString("selected_rate_type"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getObject("calculation_timestamp", OffsetDateTime.class),
            rs.getBigDecimal("current_buying_price"),
            rs.getBigDecimal("current_retail_price"),
            rs.getBigDecimal("current_lowest_price")
    );

    private static final RowMapper<RateRow> RATE_ROW_MAPPER = (rs, rowNum) -> new RateRow(
            rs.getLong("global_fx_snapshot_id"),
            rs.getBigDecimal("global_rate"),
            rs.getBigDecimal("effective_pricing_rate"),
            rs.getBigDecimal("safety_buffer_percentage"),
            rs.getString("selected_rate_type"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getObject("calculation_timestamp", OffsetDateTime.class)
    );

    private static final RowMapper<ProductCostRow> PRODUCT_COST_ROW_MAPPER = (rs, rowNum) -> new ProductCostRow(
            rs.getLong("product_id"),
            rs.getString("product_name"),
            rs.getBoolean("fx_pricing_enabled"),
            rs.getBigDecimal("replacement_cost_usd"),
            rs.getString("replacement_cost_currency"),
            rs.getBigDecimal("purchase_usd_rate"),
            rs.getObject("replacement_cost_updated_at", OffsetDateTime.class)
    );

    private static Integer nullableInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    public record ProductsPage(int page, int size, long totalItems, int totalPages, List<ProductRow> items) {
    }

    public record ProductRow(
            long productId,
            String productName,
            String category,
            String businessLineKey,
            String templateKey,
            String pricingPolicyCode,
            Integer supplierId,
            String serial,
            String barcode,
            BigDecimal stockQty,
            boolean fxPricingEnabled,
            BigDecimal replacementCostUsd,
            String replacementCostCurrency,
            BigDecimal purchaseUsdRate,
            OffsetDateTime replacementCostUpdatedAt,
            Long globalFxSnapshotId,
            BigDecimal globalRate,
            BigDecimal effectivePricingRate,
            BigDecimal safetyBufferPercentage,
            String selectedRateType,
            LocalDate effectiveDate,
            OffsetDateTime calculationTimestamp,
            BigDecimal currentBuyingPrice,
            BigDecimal currentRetailPrice,
            BigDecimal currentLowestPrice
    ) {
    }

    public record RateRow(
            long globalFxSnapshotId,
            BigDecimal globalRate,
            BigDecimal effectivePricingRate,
            BigDecimal safetyBufferPercentage,
            String selectedRateType,
            LocalDate effectiveDate,
            OffsetDateTime calculationTimestamp
    ) {
    }

    public record ProductCostRow(
            long productId,
            String productName,
            boolean fxPricingEnabled,
            BigDecimal replacementCostUsd,
            String replacementCostCurrency,
            BigDecimal purchaseUsdRate,
            OffsetDateTime replacementCostUpdatedAt
    ) {
    }
}
