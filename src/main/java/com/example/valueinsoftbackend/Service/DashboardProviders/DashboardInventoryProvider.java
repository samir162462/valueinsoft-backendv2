package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DashboardInventoryProvider {

    private final JdbcTemplate jdbcTemplate;
    private final DbBranchSettings dbBranchSettings;

    public DashboardInventoryProvider(JdbcTemplate jdbcTemplate, DbBranchSettings dbBranchSettings) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbBranchSettings = dbBranchSettings;
    }

    public List<DashboardSummaryResponse.DashboardAlert> getInventoryAlerts(Integer companyId, Integer branchId) {
        List<DashboardSummaryResponse.DashboardAlert> alerts = new ArrayList<>();

        // 1. Shortages (Low Stock)
        InventoryLowStockSettings lowStockSettings = resolveLowStockSettings(companyId, branchId);
        int shortagesCount = countShortages(companyId, branchId, lowStockSettings);
        if (shortagesCount > 0) {
            DashboardSummaryResponse.DashboardAlert alert = new DashboardSummaryResponse.DashboardAlert();
            alert.setId("inv_shortage");
            alert.setTitle("نواقص المخزون");
            boolean critical = shortagesCount >= lowStockSettings.criticalCount();
            alert.setType(critical ? "critical" : "warning");
            alert.setSeverity(critical ? "DANGER" : "WARNING");
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

        // 3. Bounced Back in Today's Shift
        Integer currentShiftId = findActiveShiftId(companyId, branchId);
        if (currentShiftId != null) {
            double bouncedBackTotal = calculateBouncedBackForShift(companyId, branchId, currentShiftId);
            if (bouncedBackTotal > 0) {
                DashboardSummaryResponse.DashboardAlert alert = new DashboardSummaryResponse.DashboardAlert();
                alert.setId("bounced_back");
                alert.setTitle("مرتجع الوردية الحالية");
                alert.setType("info");
                alert.setSeverity("INFO");
                alert.setCount((int) bouncedBackTotal);
                alert.setMessage("إجمالي المرتجعات في الوردية الحالية: " + String.format("%.2f", bouncedBackTotal) + " ج.م");
                alert.setActionLabel("مراجعة المرتجعات");
                alert.setTarget("PointSale");
                alert.setParams("tab=ShiftSales&shiftId=" + currentShiftId);
                alerts.add(alert);
            }
        }

        return alerts;
    }

    private Integer findActiveShiftId(Integer companyId, Integer branchId) {
        String shiftTable = TenantSqlIdentifiers.companySchema(companyId) + ".\"PosShiftPeriod\"";
        String sql = "SELECT \"PosSOID\" FROM " + shiftTable + " " +
                "WHERE \"branchId\" = ? AND status = 'OPEN' " +
                "ORDER BY \"PosSOID\" DESC LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, branchId);
        } catch (Exception e) {
            return null;
        }
    }

    private double calculateBouncedBackForShift(Integer companyId, Integer branchId, Integer shiftId) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String sql = "SELECT COALESCE(SUM(\"orderBouncedBack\"), 0)::double precision " +
                "FROM " + orderTable + " " +
                "WHERE shift_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Double.class, shiftId);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int countShortages(Integer companyId, Integer branchId, InventoryLowStockSettings settings) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String unitTable = TenantSqlIdentifiers.inventoryProductUnitTable(companyId);

        String serializedAvailableSql = "SELECT product_id, COUNT(*) FILTER (WHERE status = 'AVAILABLE') AS quantity " +
                "FROM " + unitTable + " WHERE branch_id = ? GROUP BY product_id";

        List<Object> params = new ArrayList<>();
        params.add(branchId);
        params.add(branchId);
        params.add(settings.excludeSerialized());
        params.add(settings.threshold());
        
        String sql = "SELECT COUNT(*)::integer FROM (" +
                "  SELECT p.product_name, SUM(" +
                "    CASE WHEN COALESCE(p.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL') " +
                "    THEN COALESCE(serialized_stock.quantity, 0) ELSE COALESCE(st.quantity, 0) END" +
                "  ) as total_qty " +
                "  FROM " + productTable + " p " +
                "  LEFT JOIN " + stockTable + " st ON st.product_id = p.product_id AND st.branch_id = ? " +
                "  LEFT JOIN (" + serializedAvailableSql + ") serialized_stock ON serialized_stock.product_id = p.product_id " +
                "  WHERE (? = FALSE OR COALESCE(p.tracking_type, 'QUANTITY') NOT IN ('IMEI', 'SERIAL')) " +
                "  GROUP BY p.product_name " +
                "  HAVING SUM(CASE WHEN COALESCE(p.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL') " +
                "    THEN COALESCE(serialized_stock.quantity, 0) ELSE COALESCE(st.quantity, 0) END) <= ? ";

        if (!settings.includeOutOfStock()) {
            sql += " AND SUM(CASE WHEN COALESCE(p.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL') " +
                    "THEN COALESCE(serialized_stock.quantity, 0) ELSE COALESCE(st.quantity, 0) END) > 0 ";
        }

        sql +=
                ") sub";
        
        return jdbcTemplate.queryForObject(sql, Integer.class, params.toArray());
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

    private InventoryLowStockSettings resolveLowStockSettings(Integer companyId, Integer branchId) {
        Map<String, Object> settings = dbBranchSettings.getEffectiveValueMap(companyId, branchId);
        return new InventoryLowStockSettings(
                readInt(settings, "inventory.lowStockThreshold", 5),
                readBoolean(settings, "inventory.excludeSerializedFromLowStock", true),
                readBoolean(settings, "inventory.includeOutOfStockInLowStock", true),
                readInt(settings, "inventory.lowStockCriticalCount", 10)
        );
    }

    private int readInt(Map<String, Object> settings, String key, int fallback) {
        Object value = settings.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean readBoolean(Map<String, Object> settings, String key, boolean fallback) {
        Object value = settings.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text.trim())) return true;
            if ("false".equalsIgnoreCase(text.trim())) return false;
        }
        return fallback;
    }

    private record InventoryLowStockSettings(
            int threshold,
            boolean excludeSerialized,
            boolean includeOutOfStock,
            int criticalCount
    ) {
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
