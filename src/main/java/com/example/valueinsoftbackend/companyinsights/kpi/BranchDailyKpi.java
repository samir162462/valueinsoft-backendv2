package com.example.valueinsoftbackend.companyinsights.kpi;

import java.time.LocalDate;

/**
 * Immutable branch-grain daily KPI snapshot row (maps to public.branch_daily_kpi).
 */
public record BranchDailyKpi(
        long companyId,
        long branchId,
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

        double inventoryValue,
        double inventoryQuantity,
        int lowStockCount,
        int outOfStockCount,
        double deadStockValue,
        int stockMovementCount,

        String dataQualityStatus,
        boolean branchActive,
        Integer operatingMinutesOpen,
        int sourceVersion
) {
}
