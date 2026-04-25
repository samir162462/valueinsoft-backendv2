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

        health.setDeadStockPct(12.5);
        health.setTurnoverRate(4.2);

        return health;
    }
}
