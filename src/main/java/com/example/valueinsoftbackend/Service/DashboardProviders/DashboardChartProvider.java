package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Request.SalesOfMonthRequest;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.Service.SalesAnalyticsService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardChartProvider {

    private final SalesAnalyticsService salesAnalyticsService;
    private final JdbcTemplate jdbcTemplate;

    public DashboardChartProvider(SalesAnalyticsService salesAnalyticsService, JdbcTemplate jdbcTemplate) {
        this.salesAnalyticsService = salesAnalyticsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(value = "dashboardCharts", key = "#companyId + '_' + #branchId + '_' + #date")
    public DashboardSummaryResponse.DashboardCharts getCharts(Integer companyId, Integer branchId, String date) {
        // Fetch monthly sales for the trend charts
        SalesOfMonthRequest request = new SalesOfMonthRequest();
        request.setCompanyId(companyId);
        request.setBranchId(branchId);
        request.setCurrentMonth(date); 

        List<DvSales> monthlySales = salesAnalyticsService.getMonthlySales(request);
        
        DashboardSummaryResponse.DashboardCharts charts = new DashboardSummaryResponse.DashboardCharts();
        
        List<Object> salesTrend = new ArrayList<>();
        List<Object> profitTrend = new ArrayList<>();
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        for (DvSales d : monthlySales) {
            String salesDateStr = sdf.format(d.getSalesDay());
            Map<String, Object> saleDataPoint = new HashMap<>();
            saleDataPoint.put("date", salesDateStr);
            saleDataPoint.put("value", d.getTotal());
            salesTrend.add(saleDataPoint);

            Map<String, Object> profitDataPoint = new HashMap<>();
            profitDataPoint.put("date", salesDateStr);
            profitDataPoint.put("value", d.getIncome());
            profitTrend.add(profitDataPoint);
        }

        charts.setSalesTrend(salesTrend);
        charts.setProfitTrend(profitTrend);
        
        // Calculate daily performance for current week
        charts.setDailyPerformance(calculateDailyPerformance(companyId, branchId));
        
        // Calculate monthly client growth trend
        charts.setClientTrend(calculateClientTrend(companyId));
        
        // Calculate payment methods distribution
        charts.setPaymentMethods(calculatePaymentMethods(companyId, branchId));

        return charts;
    }

    private List<Object> calculatePaymentMethods(Integer companyId, Integer branchId) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        
        // Aggregate totals by payType for the last 30 days
        String sql = "SELECT \"payType\", SUM(\"orderTotal\" - \"orderBouncedBack\") as total_amount " +
                "FROM " + orderTable + " " +
                "WHERE \"orderTime\" >= (CURRENT_DATE - INTERVAL '30 days') " +
                "GROUP BY \"payType\"";

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            List<Object> distribution = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> point = new HashMap<>();
                point.put("type", row.get("payType"));
                point.put("value", row.get("total_amount"));
                distribution.add(point);
            }
            return distribution;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Object> calculateClientTrend(Integer companyId) {
        String clientTable = TenantSqlIdentifiers.clientTable(companyId);
        
        // Count new clients per month for current year
        String sql = "SELECT " +
                "  TO_CHAR(\"registeredTime\", 'MON') as month_label, " +
                "  COUNT(*)::integer as new_clients, " +
                "  EXTRACT(MONTH FROM \"registeredTime\") as month_num " +
                "FROM " + clientTable + " " +
                "WHERE EXTRACT(YEAR FROM \"registeredTime\") = EXTRACT(YEAR FROM CURRENT_DATE) " +
                "GROUP BY month_label, month_num " +
                "ORDER BY month_num";

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            List<Object> trend = new ArrayList<>();
            
            // We want to show a cumulative growth trend
            int runningTotal = 0;
            
            // Initial baseline (clients from previous years)
            String baselineSql = "SELECT COUNT(*)::integer FROM " + clientTable + " WHERE EXTRACT(YEAR FROM \"registeredTime\") < EXTRACT(YEAR FROM CURRENT_DATE)";
            try {
                runningTotal = jdbcTemplate.queryForObject(baselineSql, Integer.class);
            } catch (Exception e) {}

            for (Map<String, Object> row : rows) {
                int newInMonth = (int) row.get("new_clients");
                runningTotal += newInMonth;
                
                Map<String, Object> point = new HashMap<>();
                point.put("month", row.get("month_label").toString().toUpperCase());
                point.put("value", runningTotal);
                trend.add(point);
            }
            return trend;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Object> calculateDailyPerformance(Integer companyId, Integer branchId) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        
        // Query to get sales sum per day for the current week
        String sql = "SELECT " +
                "  TO_CHAR(\"orderTime\", 'DY') as day_label, " +
                "  SUM(\"orderTotal\" - \"orderBouncedBack\") as total_sales, " +
                "  EXTRACT(DOW FROM \"orderTime\") as dow " +
                "FROM " + orderTable + " " +
                "WHERE \"orderTime\" >= ? " +
                "GROUP BY day_label, dow " +
                "ORDER BY (CASE WHEN EXTRACT(DOW FROM \"orderTime\") = 6 THEN 0 ELSE EXTRACT(DOW FROM \"orderTime\") + 1 END)";

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, weekStart);
            List<Object> performance = new ArrayList<>();
            
            // Map to standard English day labels for the frontend to handle localization
            for (Map<String, Object> row : rows) {
                Map<String, Object> point = new HashMap<>();
                point.put("day", row.get("day_label").toString().toUpperCase()); // SUN, MON...
                point.put("value", row.get("total_sales"));
                performance.add(point);
            }
            return performance;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
