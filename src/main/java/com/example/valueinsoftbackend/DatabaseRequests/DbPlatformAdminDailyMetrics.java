package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewMetricsSnapshot;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformRevenueTrendPoint;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformRevenueTrendResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DbPlatformAdminDailyMetrics {

    private static final RowMapper<PlatformOverviewMetricsSnapshot> OVERVIEW_SNAPSHOT_ROW_MAPPER = (rs, rowNum) ->
            new PlatformOverviewMetricsSnapshot(
                    rs.getDate("metric_date"),
                    rs.getInt("tenants_represented"),
                    getBigDecimal(rs.getObject("sales_amount")),
                    getBigDecimal(rs.getObject("expense_amount")),
                    getBigDecimal(rs.getObject("collected_amount")),
                    getBigDecimal(rs.getObject("net_amount"))
            );

    private static final RowMapper<PlatformRevenueTrendPoint> REVENUE_TREND_ROW_MAPPER = (rs, rowNum) ->
            new PlatformRevenueTrendPoint(
                    rs.getDate("metric_date"),
                    getBigDecimal(rs.getObject("sales_amount")),
                    getBigDecimal(rs.getObject("expense_amount")),
                    getBigDecimal(rs.getObject("collected_amount")),
                    getBigDecimal(rs.getObject("net_amount")),
                    rs.getInt("tenant_count_represented")
            );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformAdminDailyMetrics(JdbcTemplate jdbcTemplate,
                                       NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public ArrayList<Integer> getTenantIds(Integer tenantId) {
        if (tenantId != null) {
            return new ArrayList<>(List.of(tenantId));
        }
        return new ArrayList<>(
                jdbcTemplate.query(
                        "SELECT tenant_id FROM public.tenants ORDER BY tenant_id ASC",
                        (rs, rowNum) -> rs.getInt("tenant_id")
                )
        );
    }

    public BigDecimal getCompanyCollectedAmountForDate(int companyId, LocalDate metricDate) {
        String sql = "SELECT COALESCE(SUM(amount::money::numeric), 0) FROM " +
                TenantSqlIdentifiers.clientReceiptsTable(companyId) +
                " WHERE \"time\"::date = ?";
        return getBigDecimal(jdbcTemplate.queryForObject(sql, Object.class, Date.valueOf(metricDate)));
    }

    public BigDecimal getCompanyExpenseAmountForDate(int companyId, LocalDate metricDate) {
        String sql = "SELECT COALESCE(SUM(amount::money::numeric), 0) FROM " +
                TenantSqlIdentifiers.expensesTable(companyId, false) +
                " WHERE \"time\"::date = ?";
        return getBigDecimal(jdbcTemplate.queryForObject(sql, Object.class, Date.valueOf(metricDate)));
    }

    public int getTenantUserCount(int tenantId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.users u " +
                        "JOIN public.\"Branch\" b ON b.\"branchId\" = u.\"branchId\" " +
                        "WHERE b.\"companyId\" = ?",
                Integer.class,
                tenantId
        );
        return value == null ? 0 : value;
    }

    public int getTenantClientCount(int companyId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.clientTable(companyId),
                Integer.class
        );
        return value == null ? 0 : value;
    }

    public int getBranchClientCount(int companyId, int branchId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.clientTable(companyId) + " WHERE \"branchId\" = ?",
                Integer.class,
                branchId
        );
        return value == null ? 0 : value;
    }

    public int getBranchProductCount(int companyId, int branchId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId) + " WHERE branch_id = ? AND quantity > 0",
                Integer.class
                ,
                branchId
        );
        return value == null ? 0 : value;
    }

    public int getTenantUnpaidBranchSubscriptions(int tenantId) {
        Integer value = jdbcTemplate.queryForObject(
                "WITH latest_subscriptions AS (" +
                        " SELECT DISTINCT ON (cs.\"branchId\") cs.\"branchId\", COALESCE(cs.status, 'NP') AS status " +
                        " FROM public.\"CompanySubscription\" cs " +
                        " JOIN public.\"Branch\" b ON b.\"branchId\" = cs.\"branchId\" " +
                        " WHERE b.\"companyId\" = ? " +
                        " ORDER BY cs.\"branchId\", cs.\"sId\" DESC" +
                        ") " +
                        "SELECT COUNT(*) FROM latest_subscriptions WHERE status <> 'PD'",
                Integer.class,
                tenantId
        );
        return value == null ? 0 : value;
    }

    public String getBranchStatus(int branchId) {
        List<String> results = jdbcTemplate.query(
                "SELECT status FROM public.branch_runtime_states WHERE branch_id = ?",
                (rs, rowNum) -> rs.getString("status"),
                branchId
        );
        return results.isEmpty() ? "active" : results.get(0);
    }

    public int getBranchActiveUsersCount(int companyId, int branchId, LocalDate metricDate) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT \"salesUser\") FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) +
                        " WHERE \"orderTime\"::date = ? AND COALESCE(NULLIF(TRIM(\"salesUser\"), ''), NULL) IS NOT NULL",
                Integer.class,
                Date.valueOf(metricDate)
        );
        return value == null ? 0 : value;
    }

    public int getBranchShiftCount(int companyId, int branchId, LocalDate metricDate) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                        " WHERE \"branchId\" = ? AND \"ShiftStartTime\"::date = ?",
                Integer.class,
                branchId,
                Date.valueOf(metricDate)
        );
        return value == null ? 0 : value;
    }

    public int getBranchSalesCount(int companyId, int branchId, LocalDate metricDate) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) +
                        " WHERE \"orderTime\"::date = ?",
                Integer.class,
                Date.valueOf(metricDate)
        );
        return value == null ? 0 : value;
    }

    public BigDecimal getBranchSalesAmount(int companyId, int branchId, LocalDate metricDate) {
        Object value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(\"orderTotal\", 0) - COALESCE(\"orderBouncedBack\", 0)), 0)::numeric " +
                        "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) +
                        " WHERE \"orderTime\"::date = ?",
                Object.class,
                Date.valueOf(metricDate)
        );
        return getBigDecimal(value);
    }

    public int getBranchInventoryAdjustmentCount(int companyId, int branchId, LocalDate metricDate) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) +
                        " WHERE branch_id = ? AND created_at::date = ? AND COALESCE(movement_type, '') NOT IN ('SALE_OUT', 'BOUNCE_BACK_IN')",
                Integer.class,
                branchId,
                Date.valueOf(metricDate)
        );
        return value == null ? 0 : value;
    }

    public void upsertTenantDailyMetric(LocalDate metricDate,
                                        int tenantId,
                                        int branchCount,
                                        int userCount,
                                        int clientCount,
                                        int productCount,
                                        int activeBranchCount,
                                        int lockedBranchCount,
                                        int unpaidBranchSubscriptions,
                                        BigDecimal collectedAmount,
                                        BigDecimal salesAmount,
                                        BigDecimal expenseAmount) {
        jdbcTemplate.update(
                "INSERT INTO public.tenant_daily_metrics " +
                        "(metric_date, tenant_id, branch_count, user_count, client_count, product_count, active_branch_count, locked_branch_count, " +
                        "unpaid_branch_subscriptions, collected_amount, sales_amount, expense_amount) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (metric_date, tenant_id) DO UPDATE SET " +
                        "branch_count = EXCLUDED.branch_count, " +
                        "user_count = EXCLUDED.user_count, " +
                        "client_count = EXCLUDED.client_count, " +
                        "product_count = EXCLUDED.product_count, " +
                        "active_branch_count = EXCLUDED.active_branch_count, " +
                        "locked_branch_count = EXCLUDED.locked_branch_count, " +
                        "unpaid_branch_subscriptions = EXCLUDED.unpaid_branch_subscriptions, " +
                        "collected_amount = EXCLUDED.collected_amount, " +
                        "sales_amount = EXCLUDED.sales_amount, " +
                        "expense_amount = EXCLUDED.expense_amount, " +
                        "updated_at = NOW()",
                Date.valueOf(metricDate),
                tenantId,
                branchCount,
                userCount,
                clientCount,
                productCount,
                activeBranchCount,
                lockedBranchCount,
                unpaidBranchSubscriptions,
                collectedAmount,
                salesAmount,
                expenseAmount
        );
    }

    public void upsertBranchDailyMetric(LocalDate metricDate,
                                        int tenantId,
                                        int branchId,
                                        String branchStatus,
                                        int activeUsersCount,
                                        int clientCount,
                                        int productCount,
                                        int shiftCount,
                                        int salesCount,
                                        BigDecimal salesAmount,
                                        int inventoryAdjustmentCount) {
        jdbcTemplate.update(
                "INSERT INTO public.branch_daily_metrics " +
                        "(metric_date, tenant_id, branch_id, branch_status, active_users_count, client_count, product_count, shift_count, " +
                        "sales_count, sales_amount, inventory_adjustment_count) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (metric_date, branch_id) DO UPDATE SET " +
                        "tenant_id = EXCLUDED.tenant_id, " +
                        "branch_status = EXCLUDED.branch_status, " +
                        "active_users_count = EXCLUDED.active_users_count, " +
                        "client_count = EXCLUDED.client_count, " +
                        "product_count = EXCLUDED.product_count, " +
                        "shift_count = EXCLUDED.shift_count, " +
                        "sales_count = EXCLUDED.sales_count, " +
                        "sales_amount = EXCLUDED.sales_amount, " +
                        "inventory_adjustment_count = EXCLUDED.inventory_adjustment_count, " +
                        "updated_at = NOW()",
                Date.valueOf(metricDate),
                tenantId,
                branchId,
                branchStatus,
                activeUsersCount,
                clientCount,
                productCount,
                shiftCount,
                salesCount,
                salesAmount,
                inventoryAdjustmentCount
        );
    }

    public PlatformRevenueTrendResponse getRevenueTrend(int days, Integer tenantId, String packageId) {
        int safeDays = Math.max(1, Math.min(days, 365));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dateFrom", Date.valueOf(LocalDate.now().minusDays(safeDays - 1L)));

        StringBuilder whereClause = new StringBuilder(" WHERE tdm.metric_date >= :dateFrom ");
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND tdm.tenant_id = :tenantId ");
        }
        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            whereClause.append(" AND t.package_id = :packageId ");
        }

        String sql = "SELECT tdm.metric_date, " +
                "COALESCE(SUM(tdm.sales_amount), 0) AS sales_amount, " +
                "COALESCE(SUM(tdm.expense_amount), 0) AS expense_amount, " +
                "COALESCE(SUM(tdm.collected_amount), 0) AS collected_amount, " +
                "COALESCE(SUM(tdm.sales_amount - tdm.expense_amount), 0) AS net_amount, " +
                "COUNT(DISTINCT tdm.tenant_id) AS tenant_count_represented " +
                "FROM public.tenant_daily_metrics tdm " +
                "JOIN public.tenants t ON t.tenant_id = tdm.tenant_id " +
                whereClause +
                "GROUP BY tdm.metric_date " +
                "ORDER BY tdm.metric_date ASC";

        ArrayList<PlatformRevenueTrendPoint> points = new ArrayList<>(
                namedParameterJdbcTemplate.query(sql, params, REVENUE_TREND_ROW_MAPPER)
        );
        return new PlatformRevenueTrendResponse(
                tenantId,
                packageId == null || packageId.trim().isEmpty() ? null : packageId.trim(),
                safeDays,
                points,
                new Timestamp(System.currentTimeMillis())
        );
    }

    public PlatformOverviewMetricsSnapshot getLatestOverviewSnapshot() {
        String sql = "WITH latest_date AS (" +
                " SELECT MAX(metric_date) AS metric_date FROM public.tenant_daily_metrics" +
                ") " +
                "SELECT tdm.metric_date, COUNT(DISTINCT tdm.tenant_id) AS tenants_represented, " +
                "COALESCE(SUM(tdm.sales_amount), 0) AS sales_amount, " +
                "COALESCE(SUM(tdm.expense_amount), 0) AS expense_amount, " +
                "COALESCE(SUM(tdm.collected_amount), 0) AS collected_amount, " +
                "COALESCE(SUM(tdm.sales_amount - tdm.expense_amount), 0) AS net_amount " +
                "FROM public.tenant_daily_metrics tdm " +
                "JOIN latest_date ld ON ld.metric_date = tdm.metric_date " +
                "GROUP BY tdm.metric_date";

        List<PlatformOverviewMetricsSnapshot> snapshots = jdbcTemplate.query(sql, OVERVIEW_SNAPSHOT_ROW_MAPPER);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    private static BigDecimal getBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
