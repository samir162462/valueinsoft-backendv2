package com.example.valueinsoftbackend.companyinsights.kpi;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Read/write access to the company KPI snapshot tables (public schema, company-aware).
 * All writes are idempotent upserts so scheduled jobs can safely re-run.
 */
@Repository
public class CompanyKpiRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CompanyKpiRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertBranchDaily(BranchDailyKpi kpi) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", kpi.companyId())
                .addValue("branchId", kpi.branchId())
                .addValue("businessDate", kpi.businessDate())
                .addValue("salesAmount", kpi.salesAmount())
                .addValue("grossProfitAmount", kpi.grossProfitAmount())
                .addValue("grossMarginPct", kpi.grossMarginPct())
                .addValue("ordersCount", kpi.ordersCount())
                .addValue("avgOrderValue", kpi.avgOrderValue())
                .addValue("discountAmount", kpi.discountAmount())
                .addValue("returnAmount", kpi.returnAmount())
                .addValue("expensesAmount", kpi.expensesAmount())
                .addValue("netProfitAmount", kpi.netProfitAmount())
                .addValue("inventoryValue", kpi.inventoryValue())
                .addValue("inventoryQuantity", kpi.inventoryQuantity())
                .addValue("lowStockCount", kpi.lowStockCount())
                .addValue("outOfStockCount", kpi.outOfStockCount())
                .addValue("deadStockValue", kpi.deadStockValue())
                .addValue("stockMovementCount", kpi.stockMovementCount())
                .addValue("dataQualityStatus", kpi.dataQualityStatus())
                .addValue("branchActive", kpi.branchActive())
                .addValue("operatingMinutesOpen", kpi.operatingMinutesOpen())
                .addValue("sourceVersion", kpi.sourceVersion());

        jdbcTemplate.update(
                """
                        INSERT INTO public.branch_daily_kpi
                            (company_id, branch_id, business_date,
                             sales_amount, gross_profit_amount, gross_margin_pct, orders_count, avg_order_value,
                             discount_amount, return_amount, expenses_amount, net_profit_amount,
                             inventory_value, inventory_quantity, low_stock_count, out_of_stock_count,
                             dead_stock_value, stock_movement_count,
                             data_quality_status, is_branch_active, operating_minutes_open, source_version, computed_at)
                        VALUES
                            (:companyId, :branchId, :businessDate,
                             :salesAmount, :grossProfitAmount, :grossMarginPct, :ordersCount, :avgOrderValue,
                             :discountAmount, :returnAmount, :expensesAmount, :netProfitAmount,
                             :inventoryValue, :inventoryQuantity, :lowStockCount, :outOfStockCount,
                             :deadStockValue, :stockMovementCount,
                             :dataQualityStatus, :branchActive, :operatingMinutesOpen, :sourceVersion, now())
                        ON CONFLICT (company_id, branch_id, business_date) DO UPDATE SET
                             sales_amount = EXCLUDED.sales_amount,
                             gross_profit_amount = EXCLUDED.gross_profit_amount,
                             gross_margin_pct = EXCLUDED.gross_margin_pct,
                             orders_count = EXCLUDED.orders_count,
                             avg_order_value = EXCLUDED.avg_order_value,
                             discount_amount = EXCLUDED.discount_amount,
                             return_amount = EXCLUDED.return_amount,
                             expenses_amount = EXCLUDED.expenses_amount,
                             net_profit_amount = EXCLUDED.net_profit_amount,
                             inventory_value = EXCLUDED.inventory_value,
                             inventory_quantity = EXCLUDED.inventory_quantity,
                             low_stock_count = EXCLUDED.low_stock_count,
                             out_of_stock_count = EXCLUDED.out_of_stock_count,
                             dead_stock_value = EXCLUDED.dead_stock_value,
                             stock_movement_count = EXCLUDED.stock_movement_count,
                             data_quality_status = EXCLUDED.data_quality_status,
                             is_branch_active = EXCLUDED.is_branch_active,
                             operating_minutes_open = EXCLUDED.operating_minutes_open,
                             source_version = EXCLUDED.source_version,
                             computed_at = now()
                        """,
                params
        );
    }

    /**
     * Recompute and upsert the company-grain row for a date as the aggregate of its
     * branch rows. Margin % and AOV are recomputed from aggregated numerator/denominator.
     */
    public void rebuildCompanyDailyFromBranches(long companyId, LocalDate businessDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("businessDate", businessDate);

        jdbcTemplate.update(
                """
                        INSERT INTO public.company_daily_kpi
                            (company_id, business_date,
                             sales_amount, gross_profit_amount, gross_margin_pct, orders_count, avg_order_value,
                             discount_amount, return_amount, expenses_amount, net_profit_amount,
                             inventory_value, inventory_quantity, low_stock_count, out_of_stock_count,
                             dead_stock_value, stock_movement_count,
                             branch_count, branches_with_complete_data, computed_at)
                        SELECT
                             b.company_id, b.business_date,
                             SUM(b.sales_amount), SUM(b.gross_profit_amount),
                             CASE WHEN SUM(b.sales_amount) > 0
                                  THEN ROUND(SUM(b.gross_profit_amount) / SUM(b.sales_amount) * 100.0, 2)
                                  ELSE 0 END,
                             SUM(b.orders_count),
                             CASE WHEN SUM(b.orders_count) > 0
                                  THEN ROUND(SUM(b.sales_amount) / SUM(b.orders_count), 2)
                                  ELSE 0 END,
                             SUM(b.discount_amount), SUM(b.return_amount), SUM(b.expenses_amount), SUM(b.net_profit_amount),
                             SUM(b.inventory_value), SUM(b.inventory_quantity), SUM(b.low_stock_count), SUM(b.out_of_stock_count),
                             SUM(b.dead_stock_value), SUM(b.stock_movement_count),
                             COUNT(*),
                             COUNT(*) FILTER (WHERE b.data_quality_status = 'COMPLETE'),
                             now()
                        FROM public.branch_daily_kpi b
                        WHERE b.company_id = :companyId AND b.business_date = :businessDate
                        GROUP BY b.company_id, b.business_date
                        ON CONFLICT (company_id, business_date) DO UPDATE SET
                             sales_amount = EXCLUDED.sales_amount,
                             gross_profit_amount = EXCLUDED.gross_profit_amount,
                             gross_margin_pct = EXCLUDED.gross_margin_pct,
                             orders_count = EXCLUDED.orders_count,
                             avg_order_value = EXCLUDED.avg_order_value,
                             discount_amount = EXCLUDED.discount_amount,
                             return_amount = EXCLUDED.return_amount,
                             expenses_amount = EXCLUDED.expenses_amount,
                             net_profit_amount = EXCLUDED.net_profit_amount,
                             inventory_value = EXCLUDED.inventory_value,
                             inventory_quantity = EXCLUDED.inventory_quantity,
                             low_stock_count = EXCLUDED.low_stock_count,
                             out_of_stock_count = EXCLUDED.out_of_stock_count,
                             dead_stock_value = EXCLUDED.dead_stock_value,
                             stock_movement_count = EXCLUDED.stock_movement_count,
                             branch_count = EXCLUDED.branch_count,
                             branches_with_complete_data = EXCLUDED.branches_with_complete_data,
                             computed_at = now()
                        """,
                params
        );
    }

    /**
     * Company-grain rows over an inclusive date range, ascending, for trend/comparison reads.
     */
    public List<CompanyDailyKpiRow> findCompanyDailyRange(long companyId, LocalDate fromInclusive, LocalDate toInclusive) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fromDate", fromInclusive)
                .addValue("toDate", toInclusive);
        return jdbcTemplate.query(
                """
                        SELECT company_id, business_date, sales_amount, gross_profit_amount, gross_margin_pct,
                               orders_count, avg_order_value, discount_amount, return_amount, expenses_amount,
                               net_profit_amount, branch_count, branches_with_complete_data
                        FROM public.company_daily_kpi
                        WHERE company_id = :companyId AND business_date BETWEEN :fromDate AND :toDate
                        ORDER BY business_date ASC
                        """,
                params,
                (rs, rowNum) -> new CompanyDailyKpiRow(
                        rs.getLong("company_id"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getDouble("sales_amount"),
                        rs.getDouble("gross_profit_amount"),
                        rs.getDouble("gross_margin_pct"),
                        rs.getInt("orders_count"),
                        rs.getDouble("avg_order_value"),
                        rs.getDouble("discount_amount"),
                        rs.getDouble("return_amount"),
                        rs.getDouble("expenses_amount"),
                        rs.getDouble("net_profit_amount"),
                        rs.getInt("branch_count"),
                        rs.getInt("branches_with_complete_data")
                )
        );
    }

    /**
     * Branch-grain rows over an inclusive date range for per-branch comparisons.
     */
    public List<BranchDailyKpiRow> findBranchDailyRange(long companyId, LocalDate fromInclusive, LocalDate toInclusive) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fromDate", fromInclusive)
                .addValue("toDate", toInclusive);
        return jdbcTemplate.query(
                """
                        SELECT company_id, branch_id, business_date, sales_amount, gross_profit_amount,
                               gross_margin_pct, orders_count, data_quality_status, is_branch_active, operating_minutes_open
                        FROM public.branch_daily_kpi
                        WHERE company_id = :companyId AND business_date BETWEEN :fromDate AND :toDate
                        ORDER BY branch_id, business_date ASC
                        """,
                params,
                (rs, rowNum) -> new BranchDailyKpiRow(
                        rs.getLong("company_id"),
                        rs.getLong("branch_id"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getDouble("sales_amount"),
                        rs.getDouble("gross_profit_amount"),
                        rs.getDouble("gross_margin_pct"),
                        rs.getInt("orders_count"),
                        rs.getString("data_quality_status"),
                        rs.getBoolean("is_branch_active"),
                        (Integer) rs.getObject("operating_minutes_open")
                )
        );
    }

    public record BranchDailyKpiRow(
            long companyId,
            long branchId,
            LocalDate businessDate,
            double salesAmount,
            double grossProfitAmount,
            double grossMarginPct,
            int ordersCount,
            String dataQualityStatus,
            boolean branchActive,
            Integer operatingMinutesOpen
    ) {
    }

    public record CompanyDailyKpiRow(
            long companyId,
            LocalDate businessDate,
            double salesAmount,
            double grossProfitAmount,
            double grossMarginPct,
            int ordersCount,
            double avgOrderValue,
            double discountAmount,
            double returnAmount,
            double expensesAmount,
            double netProfitAmount,
            int branchCount,
            int branchesWithCompleteData
    ) {
    }
}
