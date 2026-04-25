package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardInventoryProvider {

    private final JdbcTemplate jdbcTemplate;

    public DashboardInventoryProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DashboardSummaryResponse.DashboardAlert> getInventoryAlerts(Integer companyId, Integer branchId) {
        List<DashboardSummaryResponse.DashboardAlert> alerts = new ArrayList<>();

        // 1. Shortages (Low Stock)
        int shortagesCount = countShortages(companyId, branchId);
        if (shortagesCount > 0) {
            DashboardSummaryResponse.DashboardAlert alert = new DashboardSummaryResponse.DashboardAlert();
            alert.setId("inv_shortage");
            alert.setTitle("نواقص المخزون");
            alert.setType("warning");
            alert.setSeverity("WARNING");
            alert.setCount(shortagesCount);
            alert.setMessage("يوجد " + shortagesCount + " أصناف شارفت على الانتهاء");
            alert.setActionLabel("عرض النواقص");
            alert.setTarget("viewInventory");
            alert.setParams("chip=LOW_STOCK");
            alerts.add(alert);
        }

        // 2. Out of Stock
        int outOfStockCount = countOutOfStock(companyId, branchId);
        if (outOfStockCount > 0) {
            DashboardSummaryResponse.DashboardAlert alert = new DashboardSummaryResponse.DashboardAlert();
            alert.setId("inv_out_of_stock");
            alert.setTitle("أصناف منتهية");
            alert.setType("critical");
            alert.setSeverity("DANGER");
            alert.setCount(outOfStockCount);
            alert.setMessage("يوجد " + outOfStockCount + " أصناف رصيدها صفر");
            alert.setActionLabel("عرض المنتجات");
            alert.setTarget("viewInventory");
            alert.setParams("chip=OUT_OF_STOCK");
            alerts.add(alert);
        }

        return alerts;
    }

    private int countShortages(Integer companyId, Integer branchId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        
        String sql = "SELECT COUNT(*)::integer FROM (" +
                "  SELECT p.product_name, SUM(COALESCE(st.quantity, 0)) as total_qty " +
                "  FROM " + productTable + " p " +
                "  JOIN " + stockTable + " st ON st.product_id = p.product_id AND st.branch_id = ? " +
                "  GROUP BY p.product_name " +
                "  HAVING SUM(COALESCE(st.quantity, 0)) > 0 AND SUM(COALESCE(st.quantity, 0)) <= 5" +
                ") sub";
        
        return jdbcTemplate.queryForObject(sql, Integer.class, branchId);
    }

    private int countOutOfStock(Integer companyId, Integer branchId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        
        String sql = "SELECT COUNT(*)::integer FROM (" +
                "  SELECT p.product_name, SUM(COALESCE(st.quantity, 0)) as total_qty " +
                "  FROM " + productTable + " p " +
                "  JOIN " + stockTable + " st ON st.product_id = p.product_id AND st.branch_id = ? " +
                "  GROUP BY p.product_name " +
                "  HAVING SUM(COALESCE(st.quantity, 0)) <= 0" +
                ") sub";
        
        return jdbcTemplate.queryForObject(sql, Integer.class, branchId);
    }

    public DashboardSummaryResponse.DashboardInventoryHealth getInventoryHealth(Integer companyId, Integer branchId) {
        DashboardSummaryResponse.DashboardInventoryHealth health = new DashboardSummaryResponse.DashboardInventoryHealth();

        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);

        // 1. Inventory Value (buying_price * quantity)
        String valueSql = "SELECT SUM(COALESCE(st.quantity, 0) * COALESCE(p.buying_price, 0))::double precision " +
                "FROM " + productTable + " p " +
                "JOIN " + stockTable + " st ON st.product_id = p.product_id AND st.branch_id = ?";
        
        try {
            Double totalValueRaw = jdbcTemplate.queryForObject(valueSql, Double.class, branchId);
            health.setInventoryValue(totalValueRaw != null ? totalValueRaw : 0.0);
        } catch (Exception e) {
            health.setInventoryValue(0.0);
        }

        // 2. Stock Availability (Percentage of products with stock > 0)
        String availabilitySql = "SELECT " +
                "  (COUNT(CASE WHEN total_qty > 0 THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0))::double precision " +
                "FROM (" +
                "  SELECT product_id, SUM(quantity) as total_qty FROM " + stockTable +
                "  WHERE branch_id = ? GROUP BY product_id" +
                ") sub";
        
        try {
            Double availability = jdbcTemplate.queryForObject(availabilitySql, Double.class, branchId);
            health.setStockAvailabilityPct(availability != null ? Math.round(availability * 10.0) / 10.0 : 0.0);
        } catch (Exception e) {
            health.setStockAvailabilityPct(0.0);
        }

        health.setDeadStockPct(calculateDeadStockPct(companyId, branchId));
        health.setTurnoverRate(calculateTurnoverRate(companyId, branchId, health.getInventoryValue()));
        health.setTotalItems(calculateTotalItems(companyId, branchId));
        health.setNewItemsCount(calculateNewItemsCount(companyId));
        health.setRecentlySoldItemsCount(calculateRecentlySoldItemsCount(companyId, branchId));
        health.setRecentlyMovedItemsCount(calculateRecentlyMovedItemsCount(companyId, branchId));

        return health;
    }

    private Integer calculateRecentlySoldItemsCount(Integer companyId, Integer branchId) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String orderDetailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
        
        String sql = "SELECT COUNT(DISTINCT od.\"productId\")::integer " +
                "FROM " + orderDetailTable + " od " +
                "JOIN " + orderTable + " o ON o.\"orderId\" = od.\"orderId\" " +
                "WHERE o.\"orderTime\" >= (CURRENT_DATE - INTERVAL '7 days')";
        
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class);
        } catch (Exception e) {
            return 0;
        }
    }

    private Integer calculateRecentlyMovedItemsCount(Integer companyId, Integer branchId) {
        String transTable = TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId);
        
        // Count unique products with movements that are NOT sales (e.g., adjustments, receipts)
        String sql = "SELECT COUNT(DISTINCT \"productId\")::integer " +
                "FROM " + transTable + " " +
                "WHERE \"time\" >= (CURRENT_DATE - INTERVAL '7 days') " +
                "AND \"transactionType\" NOT IN ('Sold', 'Sale', 'BounceBackInv')";
        
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class);
        } catch (Exception e) {
            return 0;
        }
    }

    private Double calculateTotalItems(Integer companyId, Integer branchId) {
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String sql = "SELECT SUM(COALESCE(quantity, 0))::double precision FROM " + stockTable + " WHERE branch_id = ?";
        try {
            Double total = jdbcTemplate.queryForObject(sql, Double.class, branchId);
            return total != null ? total : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Integer calculateNewItemsCount(Integer companyId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        // We assume there is a created_at or updated_at column. If not, we fallback to 0.
        String sql = "SELECT COUNT(*)::integer FROM " + productTable + " WHERE created_at >= (CURRENT_DATE - INTERVAL '7 days')";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class);
        } catch (Exception e) {
            // Fallback for different column naming
            try {
                String fallbackSql = "SELECT COUNT(*)::integer FROM " + productTable + " WHERE updated_at >= (CURRENT_DATE - INTERVAL '7 days')";
                return jdbcTemplate.queryForObject(fallbackSql, Integer.class);
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    private Double calculateDeadStockPct(Integer companyId, Integer branchId) {
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String orderDetailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);

        // Percentage of products with stock > 0 that haven't sold in 30 days
        String sql = "SELECT (COUNT(CASE WHEN last_sale IS NULL OR last_sale < (CURRENT_DATE - INTERVAL '30 days') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0))::double precision " +
                "FROM (" +
                "  SELECT st.product_id, MAX(o.\"orderTime\") as last_sale " +
                "  FROM " + stockTable + " st " +
                "  LEFT JOIN " + orderDetailTable + " od ON od.\"productId\" = st.product_id " +
                "  LEFT JOIN " + orderTable + " o ON o.\"orderId\" = od.\"orderId\" " +
                "  WHERE st.branch_id = ? AND st.quantity > 0 " +
                "  GROUP BY st.product_id" +
                ") sub";
        
        try {
            Double pct = jdbcTemplate.queryForObject(sql, Double.class, branchId);
            return pct != null ? Math.round(pct * 10.0) / 10.0 : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Double calculateTurnoverRate(Integer companyId, Integer branchId, Double currentInventoryValue) {
        if (currentInventoryValue == null || currentInventoryValue <= 0) return 0.0;

        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String orderDetailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);

        // COGS for last 30 days = sum(sold_qty * buying_price)
        String cogsSql = "SELECT COALESCE(SUM(od.quantity * p.buying_price), 0)::double precision " +
                "FROM " + orderDetailTable + " od " +
                "JOIN " + orderTable + " o ON o.\"orderId\" = od.\"orderId\" " +
                "JOIN " + productTable + " p ON p.product_id = od.\"productId\" " +
                "WHERE o.\"orderTime\" >= (CURRENT_DATE - INTERVAL '30 days')";
        
        try {
            Double cogs30d = jdbcTemplate.queryForObject(cogsSql, Double.class);
            if (cogs30d == null) cogs30d = 0.0;
            
            // Annualized Turnover = (COGS_30d * 12) / InventoryValue
            double turnover = (cogs30d * 12) / currentInventoryValue;
            return Math.round(turnover * 10.0) / 10.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
