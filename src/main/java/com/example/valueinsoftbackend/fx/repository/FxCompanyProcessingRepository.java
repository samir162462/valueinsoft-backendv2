package com.example.valueinsoftbackend.fx.repository;

import com.example.valueinsoftbackend.fx.model.FxCompanyConfig;
import com.example.valueinsoftbackend.fx.model.FxProductReplacementCost;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Repository
public class FxCompanyProcessingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FxCompanyProcessingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countActiveCompanies() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public."Company" c
                JOIN public.tenants t ON t.tenant_id = c.id
                WHERE t.status = 'active'
                """,
                new MapSqlParameterSource(),
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public List<FxCompanyConfig> findActiveFxEnabledCompanies() {
        return jdbcTemplate.query(
                """
                SELECT
                    c.id AS company_id,
                    cfg.safety_buffer_percentage,
                    cfg.selected_rate_type,
                    cfg.config_version
                FROM public."Company" c
                JOIN public.tenants t ON t.tenant_id = c.id
                JOIN public.company_fx_pricing_config cfg ON cfg.company_id = c.id
                WHERE t.status = 'active'
                  AND cfg.fx_pricing_enabled = TRUE
                ORDER BY c.id ASC
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new FxCompanyConfig(
                        rs.getInt("company_id"),
                        rs.getBigDecimal("safety_buffer_percentage"),
                        rs.getString("selected_rate_type"),
                        rs.getInt("config_version")
                )
        );
    }

    public List<Integer> findActiveBranchIds(int companyId) {
        return jdbcTemplate.query(
                """
                SELECT b."branchId"
                FROM public."Branch" b
                LEFT JOIN public.branch_runtime_states brs ON brs.branch_id = b."branchId"
                WHERE b."companyId" = :companyId
                  AND COALESCE(brs.status, 'active') = 'active'
                ORDER BY b."branchId" ASC
                """,
                new MapSqlParameterSource().addValue("companyId", companyId),
                (rs, rowNum) -> rs.getInt("branchId")
        );
    }

    public void upsertCompanyEffectiveRate(long snapshotId,
                                           FxCompanyConfig config,
                                           BigDecimal effectivePricingRate) {
        jdbcTemplate.update(
                """
                INSERT INTO public.company_fx_effective_rate (
                    company_id, global_fx_snapshot_id, safety_buffer_percentage,
                    effective_pricing_rate, selected_rate_type, config_version,
                    status, calculation_timestamp
                ) VALUES (
                    :companyId, :snapshotId, :safetyBufferPercentage,
                    :effectivePricingRate, :selectedRateType, :configVersion,
                    'VALID', NOW()
                )
                ON CONFLICT (company_id, global_fx_snapshot_id) DO UPDATE
                SET safety_buffer_percentage = EXCLUDED.safety_buffer_percentage,
                    effective_pricing_rate = EXCLUDED.effective_pricing_rate,
                    selected_rate_type = EXCLUDED.selected_rate_type,
                    config_version = EXCLUDED.config_version,
                    status = 'VALID',
                    calculation_timestamp = NOW(),
                    updated_at = NOW()
                """,
                new MapSqlParameterSource()
                        .addValue("companyId", config.companyId())
                        .addValue("snapshotId", snapshotId)
                        .addValue("safetyBufferPercentage", config.safetyBufferPercentage())
                        .addValue("effectivePricingRate", effectivePricingRate)
                        .addValue("selectedRateType", config.selectedRateType())
                        .addValue("configVersion", config.configVersion())
        );
    }

    public List<FxProductReplacementCost> findFxReplacementCosts(int companyId, int maxProducts) {
        String sql = """
                SELECT product_id, replacement_cost_usd
                FROM %s
                WHERE fx_pricing_enabled = TRUE
                  AND replacement_cost_currency = 'USD'
                  AND replacement_cost_usd IS NOT NULL
                  AND replacement_cost_usd > 0
                ORDER BY updated_at DESC, product_id DESC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("limit", Math.max(1, maxProducts)),
                (rs, rowNum) -> new FxProductReplacementCost(
                        rs.getLong("product_id"),
                        rs.getBigDecimal("replacement_cost_usd")
                )
        );
    }

    public void insertProductImpact(int companyId,
                                    int branchId,
                                    long snapshotId,
                                    long productId,
                                    String productName,
                                    BigDecimal replacementCostUsd,
                                    BigDecimal effectivePricingRate,
                                    BigDecimal replacementCostEgp,
                                    BigDecimal currentBuyingPrice,
                                    long recommendationRunId,
                                    String recommendationStatus) {
        BigDecimal currentBuying = currentBuyingPrice == null
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : currentBuyingPrice.setScale(4, RoundingMode.HALF_UP);
        BigDecimal delta = replacementCostEgp.subtract(currentBuying).setScale(4, RoundingMode.HALF_UP);
        BigDecimal deltaPct = currentBuying.compareTo(BigDecimal.ZERO) <= 0
                ? null
                : delta.divide(currentBuying, 4, RoundingMode.HALF_UP);

        String sql = """
                INSERT INTO %s (
                    global_fx_snapshot_id, company_id, branch_id, product_id,
                    product_name, replacement_cost_usd, effective_pricing_rate,
                    replacement_cost_egp, current_buying_price, cost_delta_amount,
                    cost_delta_pct, recommendation_run_id, recommendation_status
                ) VALUES (
                    :snapshotId, :companyId, :branchId, :productId,
                    :productName, :replacementCostUsd, :effectivePricingRate,
                    :replacementCostEgp, :currentBuyingPrice, :costDeltaAmount,
                    :costDeltaPct, :recommendationRunId, :recommendationStatus
                )
                """.formatted(TenantSqlIdentifiers.inventoryFxProductImpactTable(companyId));

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productId", productId)
                .addValue("productName", productName)
                .addValue("replacementCostUsd", replacementCostUsd)
                .addValue("effectivePricingRate", effectivePricingRate)
                .addValue("replacementCostEgp", replacementCostEgp)
                .addValue("currentBuyingPrice", currentBuying)
                .addValue("costDeltaAmount", delta)
                .addValue("costDeltaPct", deltaPct)
                .addValue("recommendationRunId", recommendationRunId)
                .addValue("recommendationStatus", recommendationStatus));
    }

    public void recordProcessingResult(long snapshotId,
                                       int companyId,
                                       String status,
                                       BigDecimal safetyBufferPercentage,
                                       BigDecimal effectivePricingRate,
                                       int evaluatedProducts,
                                       int recommendationRuns,
                                       int recommendationsGenerated,
                                       String skippedReason,
                                       String failureMessage) {
        jdbcTemplate.update(
                """
                INSERT INTO public.global_fx_company_processing_result (
                    global_fx_snapshot_id, company_id, status,
                    safety_buffer_percentage, effective_pricing_rate,
                    evaluated_products, recommendation_runs, recommendations_generated,
                    skipped_reason, failure_message, completed_at
                ) VALUES (
                    :snapshotId, :companyId, :status,
                    :safetyBufferPercentage, :effectivePricingRate,
                    :evaluatedProducts, :recommendationRuns, :recommendationsGenerated,
                    :skippedReason, :failureMessage, NOW()
                )
                ON CONFLICT (global_fx_snapshot_id, company_id) DO UPDATE
                SET status = EXCLUDED.status,
                    safety_buffer_percentage = EXCLUDED.safety_buffer_percentage,
                    effective_pricing_rate = EXCLUDED.effective_pricing_rate,
                    evaluated_products = EXCLUDED.evaluated_products,
                    recommendation_runs = EXCLUDED.recommendation_runs,
                    recommendations_generated = EXCLUDED.recommendations_generated,
                    skipped_reason = EXCLUDED.skipped_reason,
                    failure_message = EXCLUDED.failure_message,
                    completed_at = NOW(),
                    updated_at = NOW()
                """,
                new MapSqlParameterSource()
                        .addValue("snapshotId", snapshotId)
                        .addValue("companyId", companyId)
                        .addValue("status", status)
                        .addValue("safetyBufferPercentage", safetyBufferPercentage)
                        .addValue("effectivePricingRate", effectivePricingRate)
                        .addValue("evaluatedProducts", evaluatedProducts)
                        .addValue("recommendationRuns", recommendationRuns)
                        .addValue("recommendationsGenerated", recommendationsGenerated)
                        .addValue("skippedReason", skippedReason)
                        .addValue("failureMessage", failureMessage)
        );
    }
}
