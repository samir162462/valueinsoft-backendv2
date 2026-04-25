package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.DatabaseRequests.DbClient;
import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvSales;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DashboardKpiProvider {

    private final DbDvSales dbDvSales;
    private final DbClient dbClient;

    public DashboardKpiProvider(DbDvSales dbDvSales, DbClient dbClient) {
        this.dbDvSales = dbDvSales;
        this.dbClient = dbClient;
    }

    public KpiPackage getKpis(Integer companyId, Integer branchId, String date) {
        System.out.println("DEBUG: Fetching Dashboard KPIs for Company: " + companyId + " Branch: " + branchId + " Date: " + date);
        
        // Today's Data
        DvSales todayData = dbDvSales.getDailyKpis(companyId, branchId, date);
        int todayClients = dbClient.countNewClientsByDay(companyId, branchId, date);

        // Yesterday's Data for comparison
        java.time.LocalDate today = java.time.LocalDate.parse(date);
        String yesterdayStr = today.minusDays(1).toString();
        DvSales yesterdayData = dbDvSales.getDailyKpis(companyId, branchId, yesterdayStr);
        int yesterdayClients = dbClient.countNewClientsByDay(companyId, branchId, yesterdayStr);

        DashboardSummaryResponse.DashboardKpis kpis = new DashboardSummaryResponse.DashboardKpis();
        DashboardSummaryResponse.DashboardComparisons comparisons = new DashboardSummaryResponse.DashboardComparisons();

        if (todayData != null) {
            kpis.setTodaySales((double) todayData.getTotal());
            kpis.setGrossProfit((double) todayData.getIncome());
            kpis.setOrdersCount(todayData.getCount());
            kpis.setAvgOrderValue(todayData.getCount() > 0 ? (double) todayData.getTotal() / todayData.getCount() : 0.0);
        } else {
            kpis.setTodaySales(0.0);
            kpis.setGrossProfit(0.0);
            kpis.setOrdersCount(0);
            kpis.setAvgOrderValue(0.0);
        }
        kpis.setNewClients(todayClients);
        kpis.setCashOnHand(0.0);

        // Calculate Comparisons
        if (yesterdayData != null) {
            comparisons.setSalesVsPreviousPct(calculatePctChange(todayData != null ? todayData.getTotal() : 0, yesterdayData.getTotal()));
            comparisons.setGrossProfitVsPreviousPct(calculatePctChange(todayData != null ? todayData.getIncome() : 0, yesterdayData.getIncome()));
            comparisons.setOrdersVsPreviousPct(calculatePctChange(todayData != null ? todayData.getCount() : 0, yesterdayData.getCount()));
            
            double todayAvg = (todayData != null && todayData.getCount() > 0) ? (double) todayData.getTotal() / todayData.getCount() : 0.0;
            double yesterdayAvg = (yesterdayData.getCount() > 0) ? (double) yesterdayData.getTotal() / yesterdayData.getCount() : 0.0;
            comparisons.setAvgOrderVsPreviousPct(calculatePctChange(todayAvg, yesterdayAvg));
        }
        comparisons.setClientsVsPreviousPct(calculatePctChange(todayClients, yesterdayClients));
        comparisons.setCashVsPreviousPct(0.0); // Stable

        return new KpiPackage(kpis, comparisons);
    }

    private Double calculatePctChange(double current, double previous) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return ((current - previous) / previous) * 100.0;
    }

    public static class KpiPackage {
        public final DashboardSummaryResponse.DashboardKpis kpis;
        public final DashboardSummaryResponse.DashboardComparisons comparisons;
        public KpiPackage(DashboardSummaryResponse.DashboardKpis kpis, DashboardSummaryResponse.DashboardComparisons comparisons) {
            this.kpis = kpis;
            this.comparisons = comparisons;
        }
    }
}
