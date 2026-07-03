package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.Service.kpi.CompanyKpiCalculator;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Component
public class DashboardProfitProvider {

    private final CompanyKpiCalculator kpiCalculator;

    public DashboardProfitProvider(CompanyKpiCalculator kpiCalculator) {
        this.kpiCalculator = kpiCalculator;
    }

    public DashboardSummaryResponse.DashboardProfitSummary getProfitSummary(int companyId, int branchId, LocalDate baseDate) {
        DashboardSummaryResponse.DashboardProfitSummary summary = new DashboardSummaryResponse.DashboardProfitSummary();

        // 1. Week (Saturday to Friday - Egypt Standard)
        LocalDate weekStart = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        LocalDate weekEnd = weekStart.plusDays(7);
        summary.setWeek(calculateProfitData(companyId, branchId, weekStart, weekEnd));

        // 2. Month
        LocalDate monthStart = baseDate.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate monthEnd = monthStart.plusMonths(1);
        summary.setMonth(calculateProfitData(companyId, branchId, monthStart, monthEnd));

        // 3. Year
        LocalDate yearStart = baseDate.with(TemporalAdjusters.firstDayOfYear());
        LocalDate yearEnd = yearStart.plusYears(1);
        summary.setYear(calculateProfitData(companyId, branchId, yearStart, yearEnd));

        return summary;
    }

    private DashboardSummaryResponse.DashboardProfitSummary.ProfitData calculateProfitData(int companyId, int branchId, LocalDate start, LocalDate end) {
        DashboardSummaryResponse.DashboardProfitSummary.ProfitData data = new DashboardSummaryResponse.DashboardProfitSummary.ProfitData();

        // Revenue / Gross Profit come from the canonical KPI calculation layer so that
        // dashboards, reports, and Company Smart Insights all use identical arithmetic.
        CompanyKpiCalculator.BranchSalesProfit salesProfit = kpiCalculator.salesProfit(companyId, branchId, start, end);
        data.setRevenue(salesProfit.sales());
        data.setGrossProfit(salesProfit.grossProfit());

        // Expenses (canonical)
        data.setExpenses(kpiCalculator.expenses(companyId, branchId, start, end));

        // Net Profit = Gross Profit - Expenses
        data.setNetProfit(data.getGrossProfit() - data.getExpenses());

        // Margin = (Gross Profit / Revenue) * 100
        data.setMargin(salesProfit.grossMarginPct());

        // Set Date Range
        data.setStartDate(start.toString());
        data.setEndDate(end.minusDays(1).toString()); // End is exclusive in calculation, inclusive for display

        return data;
    }
}
