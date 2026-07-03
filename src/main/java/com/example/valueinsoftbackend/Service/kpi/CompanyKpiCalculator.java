package com.example.valueinsoftbackend.Service.kpi;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Canonical KPI calculation layer.
 *
 * <p>Single source of truth for every branch-level KPI expression. Dashboards,
 * reports, and the Company Smart Insights scheduled aggregation jobs all delegate
 * here so the exact same arithmetic is used everywhere (no duplicated SQL).
 *
 * <p>The expressions below are the ones historically embedded in
 * {@code DashboardProfitProvider} and {@code DashboardInventoryProvider}; those
 * providers are being refactored to delegate to this component (behavior-preserving).
 *
 * <p>Definitions (per branch, over a half-open period [from, to)):
 * <ul>
 *   <li>sales      = SUM("orderTotal") - SUM("orderBouncedBack")</li>
 *   <li>grossProfit= SUM("orderIncome")</li>
 *   <li>discounts  = SUM("orderDiscount")</li>
 *   <li>returns    = SUM("orderBouncedBack")</li>
 *   <li>orders     = COUNT(*)</li>
 *   <li>expenses   = SUM(amount) from tenant Expenses for the branch</li>
 *   <li>inventoryValue = SUM(stock.quantity * product.buying_price)</li>
 *   <li>inventoryQty   = SUM(stock.quantity)</li>
 * </ul>
 */
@Component
public class CompanyKpiCalculator {

    private final JdbcTemplate jdbcTemplate;

    public CompanyKpiCalculator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Sales / profit / discount / return / order-count for a branch over [from, to).
     */
    public BranchSalesProfit salesProfit(int companyId, int branchId, LocalDate fromInclusive, LocalDate toExclusive) {
        Timestamp fromTs = Timestamp.valueOf(fromInclusive.atStartOfDay());
        Timestamp toTs = Timestamp.valueOf(toExclusive.atStartOfDay());

        String sql = "SELECT " +
                "COALESCE(SUM(\"orderTotal\"), 0) - COALESCE(SUM(\"orderBouncedBack\"), 0) AS sales, " +
                "COALESCE(SUM(\"orderIncome\"), 0) AS gross_profit, " +
                "COALESCE(SUM(\"orderDiscount\"), 0) AS discount_amount, " +
                "COALESCE(SUM(\"orderBouncedBack\"), 0) AS return_amount, " +
                "COUNT(*) AS orders_count " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " " +
                "WHERE \"orderTime\" >= ? AND \"orderTime\" < ?";

        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return new BranchSalesProfit(
                        rs.getDouble("sales"),
                        rs.getDouble("gross_profit"),
                        rs.getDouble("discount_amount"),
                        rs.getDouble("return_amount"),
                        rs.getInt("orders_count")
                );
            }
            return BranchSalesProfit.empty();
        }, fromTs, toTs);
    }

    /**
     * Operational expenses for a branch over [from, to).
     */
    public double expenses(int companyId, int branchId, LocalDate fromInclusive, LocalDate toExclusive) {
        Timestamp fromTs = Timestamp.valueOf(fromInclusive.atStartOfDay());
        Timestamp toTs = Timestamp.valueOf(toExclusive.atStartOfDay());

        String sql = "SELECT COALESCE(SUM(amount::money::numeric), 0) AS total_expenses " +
                "FROM " + TenantSqlIdentifiers.expensesTable(companyId, false) + " " +
                "WHERE \"branchId\" = ? AND \"time\" >= ? AND \"time\" < ?";

        Double value = jdbcTemplate.queryForObject(sql, Double.class, branchId, fromTs, toTs);
        return value != null ? value : 0.0;
    }

    /**
     * On-hand inventory value and quantity for a branch (current balances).
     */
    public BranchInventoryLevel inventoryLevel(int companyId, int branchId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);

        String valueSql = "SELECT COALESCE(SUM(COALESCE(st.quantity, 0) * COALESCE(p.buying_price, 0)), 0)::double precision " +
                "FROM " + productTable + " p " +
                "JOIN " + stockTable + " st ON st.product_id = p.product_id AND st.branch_id = ?";
        Double value = safeDouble(() -> jdbcTemplate.queryForObject(valueSql, Double.class, branchId));

        String qtySql = "SELECT COALESCE(SUM(COALESCE(quantity, 0)), 0)::double precision " +
                "FROM " + stockTable + " WHERE branch_id = ?";
        Double qty = safeDouble(() -> jdbcTemplate.queryForObject(qtySql, Double.class, branchId));

        return new BranchInventoryLevel(value == null ? 0.0 : value, qty == null ? 0.0 : qty);
    }

    /**
     * Count of distinct products moved (any inventory transaction) over [from, to).
     */
    public int stockMovementCount(int companyId, int branchId, LocalDate fromInclusive, LocalDate toExclusive) {
        Timestamp fromTs = Timestamp.valueOf(fromInclusive.atStartOfDay());
        Timestamp toTs = Timestamp.valueOf(toExclusive.atStartOfDay());
        String transTable = TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId);
        String sql = "SELECT COUNT(DISTINCT \"productId\")::integer " +
                "FROM " + transTable + " " +
                "WHERE \"time\" >= ? AND \"time\" < ?";
        Integer count = safeInt(() -> jdbcTemplate.queryForObject(sql, Integer.class, fromTs, toTs));
        return count == null ? 0 : count;
    }

    /**
     * Low-stock and out-of-stock product counts for a branch.
     * Low-stock = on-hand quantity in (0, threshold]; out-of-stock = on-hand &lt;= 0.
     */
    public BranchStockCounts stockCounts(int companyId, int branchId, int lowStockThreshold) {
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String sql = "SELECT " +
                "  COUNT(*) FILTER (WHERE total_qty > 0 AND total_qty <= ?)::integer AS low_count, " +
                "  COUNT(*) FILTER (WHERE total_qty <= 0)::integer AS out_count " +
                "FROM (" +
                "  SELECT product_id, SUM(COALESCE(quantity, 0)) AS total_qty " +
                "  FROM " + stockTable + " WHERE branch_id = ? GROUP BY product_id" +
                ") sub";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return new BranchStockCounts(rs.getInt("low_count"), rs.getInt("out_count"));
            }
            return new BranchStockCounts(0, 0);
        }, lowStockThreshold, branchId);
    }

    /**
     * Total buying-price value of products with stock on hand that have not sold within
     * {@code noSaleDays} days (dead stock) for a branch.
     */
    public double deadStockValue(int companyId, int branchId, int noSaleDays) {
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String orderDetailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);

        String sql = "SELECT COALESCE(SUM(sub.qty * sub.buying_price), 0)::double precision FROM (" +
                "  SELECT st.product_id, " +
                "         SUM(COALESCE(st.quantity, 0)) AS qty, " +
                "         MAX(COALESCE(p.buying_price, 0)) AS buying_price, " +
                "         MAX(o.\"orderTime\") AS last_sale " +
                "  FROM " + stockTable + " st " +
                "  JOIN " + productTable + " p ON p.product_id = st.product_id " +
                "  LEFT JOIN " + orderDetailTable + " od ON od.\"productId\" = st.product_id " +
                "  LEFT JOIN " + orderTable + " o ON o.\"orderId\" = od.\"orderId\" " +
                "  WHERE st.branch_id = ? AND st.quantity > 0 " +
                "  GROUP BY st.product_id " +
                "  HAVING MAX(o.\"orderTime\") IS NULL " +
                "     OR MAX(o.\"orderTime\") < (CURRENT_DATE - make_interval(days => ?)) " +
                ") sub";
        Double value = safeDouble(() -> jdbcTemplate.queryForObject(sql, Double.class, branchId, noSaleDays));
        return value == null ? 0.0 : value;
    }

    private static Double safeDouble(java.util.function.Supplier<Double> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return 0.0;
        }
    }

    private static Integer safeInt(java.util.function.Supplier<Integer> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    // -------------------------------------------------------
    // Canonical result records
    // -------------------------------------------------------

    public record BranchSalesProfit(
            double sales,
            double grossProfit,
            double discountAmount,
            double returnAmount,
            int ordersCount
    ) {
        public static BranchSalesProfit empty() {
            return new BranchSalesProfit(0, 0, 0, 0, 0);
        }

        public double avgOrderValue() {
            return ordersCount > 0 ? sales / ordersCount : 0.0;
        }

        public double grossMarginPct() {
            return sales > 0 ? Math.round((grossProfit / sales) * 1000.0) / 10.0 : 0.0;
        }
    }

    public record BranchInventoryLevel(double value, double quantity) {
    }

    public record BranchStockCounts(int lowStockCount, int outOfStockCount) {
    }
}
