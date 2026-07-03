package com.example.valueinsoftbackend.companyinsights.lifecycle;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read/write access to {@code public.company_insight_settings}.
 */
@Repository
public class CompanyInsightSettingsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CompanyInsightSettingsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CompanyInsightThresholds> find(long companyId) {
        return jdbcTemplate.query(
                """
                        SELECT company_id, low_performing_branch_deviation_pct, critical_stock_coverage_days,
                               low_stock_multi_branch_count, dead_stock_no_sale_days, dead_stock_min_value,
                               margin_drop_pct, material_sales_drop_pct, no_sales_alert_delay_minutes,
                               min_history_days_for_comparison, new_branch_grace_days,
                               max_active_insights_per_category, insight_cooldown_hours,
                               currency_code, timezone, ai_enrichment_enabled
                        FROM public.company_insight_settings
                        WHERE company_id = :companyId
                        """,
                new MapSqlParameterSource("companyId", companyId),
                rs -> {
                    if (!rs.next()) {
                        return Optional.<CompanyInsightThresholds>empty();
                    }
                    return Optional.of(new CompanyInsightThresholds(
                            rs.getLong("company_id"),
                            rs.getBigDecimal("low_performing_branch_deviation_pct"),
                            rs.getInt("critical_stock_coverage_days"),
                            rs.getInt("low_stock_multi_branch_count"),
                            rs.getInt("dead_stock_no_sale_days"),
                            rs.getBigDecimal("dead_stock_min_value"),
                            rs.getBigDecimal("margin_drop_pct"),
                            rs.getBigDecimal("material_sales_drop_pct"),
                            rs.getInt("no_sales_alert_delay_minutes"),
                            rs.getInt("min_history_days_for_comparison"),
                            rs.getInt("new_branch_grace_days"),
                            rs.getInt("max_active_insights_per_category"),
                            rs.getInt("insight_cooldown_hours"),
                            rs.getString("currency_code"),
                            rs.getString("timezone"),
                            rs.getBoolean("ai_enrichment_enabled")
                    ));
                }
        );
    }

    public CompanyInsightThresholds save(CompanyInsightThresholds t) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", t.companyId())
                .addValue("lowPerformingBranchDeviationPct", t.lowPerformingBranchDeviationPct())
                .addValue("criticalStockCoverageDays", t.criticalStockCoverageDays())
                .addValue("lowStockMultiBranchCount", t.lowStockMultiBranchCount())
                .addValue("deadStockNoSaleDays", t.deadStockNoSaleDays())
                .addValue("deadStockMinValue", t.deadStockMinValue())
                .addValue("marginDropPct", t.marginDropPct())
                .addValue("materialSalesDropPct", t.materialSalesDropPct())
                .addValue("noSalesAlertDelayMinutes", t.noSalesAlertDelayMinutes())
                .addValue("minHistoryDaysForComparison", t.minHistoryDaysForComparison())
                .addValue("newBranchGraceDays", t.newBranchGraceDays())
                .addValue("maxActiveInsightsPerCategory", t.maxActiveInsightsPerCategory())
                .addValue("insightCooldownHours", t.insightCooldownHours())
                .addValue("currencyCode", t.currencyCode())
                .addValue("timezone", t.timezone())
                .addValue("aiEnrichmentEnabled", t.aiEnrichmentEnabled());

        jdbcTemplate.update(
                """
                        INSERT INTO public.company_insight_settings
                            (company_id, low_performing_branch_deviation_pct, critical_stock_coverage_days,
                             low_stock_multi_branch_count, dead_stock_no_sale_days, dead_stock_min_value,
                             margin_drop_pct, material_sales_drop_pct, no_sales_alert_delay_minutes,
                             min_history_days_for_comparison, new_branch_grace_days,
                             max_active_insights_per_category, insight_cooldown_hours,
                             currency_code, timezone, ai_enrichment_enabled)
                        VALUES
                            (:companyId, :lowPerformingBranchDeviationPct, :criticalStockCoverageDays,
                             :lowStockMultiBranchCount, :deadStockNoSaleDays, :deadStockMinValue,
                             :marginDropPct, :materialSalesDropPct, :noSalesAlertDelayMinutes,
                             :minHistoryDaysForComparison, :newBranchGraceDays,
                             :maxActiveInsightsPerCategory, :insightCooldownHours,
                             :currencyCode, :timezone, :aiEnrichmentEnabled)
                        ON CONFLICT (company_id) DO UPDATE SET
                             low_performing_branch_deviation_pct = EXCLUDED.low_performing_branch_deviation_pct,
                             critical_stock_coverage_days = EXCLUDED.critical_stock_coverage_days,
                             low_stock_multi_branch_count = EXCLUDED.low_stock_multi_branch_count,
                             dead_stock_no_sale_days = EXCLUDED.dead_stock_no_sale_days,
                             dead_stock_min_value = EXCLUDED.dead_stock_min_value,
                             margin_drop_pct = EXCLUDED.margin_drop_pct,
                             material_sales_drop_pct = EXCLUDED.material_sales_drop_pct,
                             no_sales_alert_delay_minutes = EXCLUDED.no_sales_alert_delay_minutes,
                             min_history_days_for_comparison = EXCLUDED.min_history_days_for_comparison,
                             new_branch_grace_days = EXCLUDED.new_branch_grace_days,
                             max_active_insights_per_category = EXCLUDED.max_active_insights_per_category,
                             insight_cooldown_hours = EXCLUDED.insight_cooldown_hours,
                             currency_code = EXCLUDED.currency_code,
                             timezone = EXCLUDED.timezone,
                             ai_enrichment_enabled = EXCLUDED.ai_enrichment_enabled
                        """,
                params
        );
        return t;
    }
}
