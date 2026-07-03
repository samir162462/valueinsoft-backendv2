package com.example.valueinsoftbackend.companyinsights.kpi;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.Service.kpi.CompanyKpiCalculator;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Builds a {@link BranchDailyKpi} for a (company, branch, businessDate) by delegating
 * every metric to the canonical {@link CompanyKpiCalculator}. No KPI SQL lives here.
 *
 * <p>Performance measures use the half-open day window [businessDate, businessDate+1).
 * Inventory levels/counts are point-in-time (current balances), matching the existing
 * dashboard inventory semantics.
 */
@Component
@Slf4j
public class BranchKpiSourceReader {

    private final CompanyKpiCalculator kpiCalculator;
    private final DbBranchSettings dbBranchSettings;
    private final CompanyInsightProperties properties;

    public BranchKpiSourceReader(CompanyKpiCalculator kpiCalculator,
                                 DbBranchSettings dbBranchSettings,
                                 CompanyInsightProperties properties) {
        this.kpiCalculator = kpiCalculator;
        this.dbBranchSettings = dbBranchSettings;
        this.properties = properties;
    }

    public BranchDailyKpi read(int companyId, int branchId, LocalDate businessDate) {
        LocalDate dayStart = businessDate;
        LocalDate dayEnd = businessDate.plusDays(1);

        String dataQuality = "COMPLETE";
        try {
            CompanyKpiCalculator.BranchSalesProfit sp =
                    kpiCalculator.salesProfit(companyId, branchId, dayStart, dayEnd);
            double expenses = kpiCalculator.expenses(companyId, branchId, dayStart, dayEnd);
            CompanyKpiCalculator.BranchInventoryLevel inv =
                    kpiCalculator.inventoryLevel(companyId, branchId);
            int lowStockThreshold = resolveLowStockThreshold(companyId, branchId);
            CompanyKpiCalculator.BranchStockCounts counts =
                    kpiCalculator.stockCounts(companyId, branchId, lowStockThreshold);
            double deadStockValue =
                    kpiCalculator.deadStockValue(companyId, branchId, properties.getSnapshotDeadStockDays());
            int movementCount =
                    kpiCalculator.stockMovementCount(companyId, branchId, dayStart, dayEnd);

            return new BranchDailyKpi(
                    companyId,
                    branchId,
                    businessDate,
                    sp.sales(),
                    sp.grossProfit(),
                    sp.grossMarginPct(),
                    sp.ordersCount(),
                    sp.avgOrderValue(),
                    sp.discountAmount(),
                    sp.returnAmount(),
                    expenses,
                    sp.grossProfit() - expenses,
                    inv.value(),
                    inv.quantity(),
                    counts.lowStockCount(),
                    counts.outOfStockCount(),
                    deadStockValue,
                    movementCount,
                    dataQuality,
                    true,
                    null,
                    1
            );
        } catch (RuntimeException exception) {
            log.warn("Branch KPI read failed companyId={} branchId={} date={} reason={}",
                    companyId, branchId, businessDate, exception.getMessage());
            // Emit a MISSING row so downstream data-quality gate can suppress alerts rather
            // than silently dropping the branch.
            return new BranchDailyKpi(
                    companyId, branchId, businessDate,
                    0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    "MISSING", true, null, 1
            );
        }
    }

    private int resolveLowStockThreshold(int companyId, int branchId) {
        try {
            Map<String, Object> settings = dbBranchSettings.getEffectiveValueMap(companyId, branchId);
            Object value = settings.get("inventory.lowStockThreshold");
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                return Integer.parseInt(text.trim());
            }
        } catch (RuntimeException ignored) {
            // fall through to default
        }
        return properties.getDefaultLowStockThreshold();
    }
}
