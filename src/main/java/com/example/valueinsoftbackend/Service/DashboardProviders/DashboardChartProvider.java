package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Request.SalesOfMonthRequest;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.Service.SalesAnalyticsService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardChartProvider {

    private final SalesAnalyticsService salesAnalyticsService;

    public DashboardChartProvider(SalesAnalyticsService salesAnalyticsService) {
        this.salesAnalyticsService = salesAnalyticsService;
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
        // Leave expenseTrend and dailyPerformance empty for now

        return charts;
    }
}
