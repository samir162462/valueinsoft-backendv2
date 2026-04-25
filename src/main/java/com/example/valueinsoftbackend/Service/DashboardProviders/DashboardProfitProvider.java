package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Component
public class DashboardProfitProvider {

    private final JdbcTemplate jdbcTemplate;

    public DashboardProfitProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

        Timestamp startTs = Timestamp.valueOf(start.atStartOfDay());
        Timestamp endTs = Timestamp.valueOf(end.atStartOfDay());

        // Calculate Revenue and Gross Profit from Orders
        String salesSql = "SELECT " +
                "COALESCE(SUM(\"orderTotal\"), 0) - COALESCE(SUM(\"orderBouncedBack\"), 0) AS revenue, " +
                "COALESCE(SUM(\"orderIncome\"), 0) AS gross_profit " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " " +
                "WHERE \"orderTime\" >= ? AND \"orderTime\" < ?";

        jdbcTemplate.query(salesSql, rs -> {
            data.setRevenue(rs.getDouble("revenue"));
            data.setGrossProfit(rs.getDouble("gross_profit"));
        }, startTs, endTs);

        // Calculate Expenses
        // Note: We check both regular and static expenses if they are recorded in the operational table
        String expensesSql = "SELECT COALESCE(SUM(amount::money::numeric), 0) AS total_expenses " +
                "FROM " + TenantSqlIdentifiers.expensesTable(companyId, false) + " " +
                "WHERE \"branchId\" = ? AND \"time\" >= ? AND \"time\" < ?";

        Double expenses = jdbcTemplate.queryForObject(expensesSql, Double.class, branchId, startTs, endTs);
        data.setExpenses(expenses != null ? expenses : 0.0);

        // Net Profit = Gross Profit - Expenses
        data.setNetProfit(data.getGrossProfit() - data.getExpenses());

        // Margin = (Gross Profit / Revenue) * 100
        if (data.getRevenue() > 0) {
            data.setMargin(Math.round((data.getGrossProfit() / data.getRevenue()) * 1000.0) / 10.0);
        } else {
            data.setMargin(0.0);
        }

        // Set Date Range
        data.setStartDate(start.toString());
        data.setEndDate(end.minusDays(1).toString()); // End is exclusive in calculation, inclusive for display

        return data;
    }
}
