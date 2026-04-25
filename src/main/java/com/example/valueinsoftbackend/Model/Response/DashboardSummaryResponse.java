package com.example.valueinsoftbackend.Model.Response;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardSummaryResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private DashboardContext context;
    private DashboardKpis kpis;
    private DashboardComparisons comparisons;
    private List<DashboardAlert> alerts = new ArrayList<>();
    private DashboardProfitSummary profitSummary;
    private DashboardInventoryHealth inventoryHealth;
    private Map<String, Integer> paymentMethods = new HashMap<>();
    private DashboardCharts charts;
    private DashboardTopPerformers topPerformers;
    private List<DashboardWarning> warnings = new ArrayList<>();
    private Map<String, String> sectionStatus = new HashMap<>();

    public DashboardSummaryResponse() {}

    public DashboardContext getContext() { return context; }
    public void setContext(DashboardContext context) { this.context = context; }

    public DashboardKpis getKpis() { return kpis; }
    public void setKpis(DashboardKpis kpis) { this.kpis = kpis; }

    public DashboardComparisons getComparisons() { return comparisons; }
    public void setComparisons(DashboardComparisons comparisons) { this.comparisons = comparisons; }

    public List<DashboardAlert> getAlerts() { return alerts; }
    public void setAlerts(List<DashboardAlert> alerts) { this.alerts = alerts; }

    public DashboardProfitSummary getProfitSummary() { return profitSummary; }
    public void setProfitSummary(DashboardProfitSummary profitSummary) { this.profitSummary = profitSummary; }

    public DashboardInventoryHealth getInventoryHealth() { return inventoryHealth; }
    public void setInventoryHealth(DashboardInventoryHealth inventoryHealth) { this.inventoryHealth = inventoryHealth; }

    public Map<String, Integer> getPaymentMethods() { return paymentMethods; }
    public void setPaymentMethods(Map<String, Integer> paymentMethods) { this.paymentMethods = paymentMethods; }

    public DashboardCharts getCharts() { return charts; }
    public void setCharts(DashboardCharts charts) { this.charts = charts; }

    public DashboardTopPerformers getTopPerformers() { return topPerformers; }
    public void setTopPerformers(DashboardTopPerformers topPerformers) { this.topPerformers = topPerformers; }

    public List<DashboardWarning> getWarnings() { return warnings; }
    public void setWarnings(List<DashboardWarning> warnings) { this.warnings = warnings; }

    public Map<String, String> getSectionStatus() { return sectionStatus; }
    public void setSectionStatus(Map<String, String> sectionStatus) { this.sectionStatus = sectionStatus; }

    public static class DashboardContext implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer branchId;
        private String branchName;
        private String date;
        private String period;
        private String currency;
        private String generatedAt;
        
        public DashboardContext() {}

        public Integer getBranchId() { return branchId; }
        public void setBranchId(Integer branchId) { this.branchId = branchId; }
        public String getBranchName() { return branchName; }
        public void setBranchName(String branchName) { this.branchName = branchName; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    }

    public static class DashboardKpis implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double todaySales;
        private Double grossProfit;
        private Integer ordersCount;
        private Integer newClients;
        private Double avgOrderValue;
        private Double cashOnHand;

        public DashboardKpis() {}

        public Double getTodaySales() { return todaySales; }
        public void setTodaySales(Double todaySales) { this.todaySales = todaySales; }
        public Double getGrossProfit() { return grossProfit; }
        public void setGrossProfit(Double grossProfit) { this.grossProfit = grossProfit; }
        public Integer getOrdersCount() { return ordersCount; }
        public void setOrdersCount(Integer ordersCount) { this.ordersCount = ordersCount; }
        public Integer getNewClients() { return newClients; }
        public void setNewClients(Integer newClients) { this.newClients = newClients; }
        public Double getAvgOrderValue() { return avgOrderValue; }
        public void setAvgOrderValue(Double avgOrderValue) { this.avgOrderValue = avgOrderValue; }
        public Double getCashOnHand() { return cashOnHand; }
        public void setCashOnHand(Double cashOnHand) { this.cashOnHand = cashOnHand; }
    }

    public static class DashboardComparisons implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double salesVsPreviousPct;
        private Double grossProfitVsPreviousPct;
        private Double ordersVsPreviousPct;
        private Double clientsVsPreviousPct;
        private Double avgOrderVsPreviousPct;
        private Double cashVsPreviousPct;

        public DashboardComparisons() {}

        public Double getSalesVsPreviousPct() { return salesVsPreviousPct; }
        public void setSalesVsPreviousPct(Double salesVsPreviousPct) { this.salesVsPreviousPct = salesVsPreviousPct; }
        public Double getGrossProfitVsPreviousPct() { return grossProfitVsPreviousPct; }
        public void setGrossProfitVsPreviousPct(Double grossProfitVsPreviousPct) { this.grossProfitVsPreviousPct = grossProfitVsPreviousPct; }
        public Double getOrdersVsPreviousPct() { return ordersVsPreviousPct; }
        public void setOrdersVsPreviousPct(Double ordersVsPreviousPct) { this.ordersVsPreviousPct = ordersVsPreviousPct; }
        public Double getClientsVsPreviousPct() { return clientsVsPreviousPct; }
        public void setClientsVsPreviousPct(Double clientsVsPreviousPct) { this.clientsVsPreviousPct = clientsVsPreviousPct; }
        public Double getAvgOrderVsPreviousPct() { return avgOrderVsPreviousPct; }
        public void setAvgOrderVsPreviousPct(Double avgOrderVsPreviousPct) { this.avgOrderVsPreviousPct = avgOrderVsPreviousPct; }
        public Double getCashVsPreviousPct() { return cashVsPreviousPct; }
        public void setCashVsPreviousPct(Double cashVsPreviousPct) { this.cashVsPreviousPct = cashVsPreviousPct; }
    }

    public static class DashboardAlert implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String title;
        private String type;
        private String severity;
        private Object count;
        private String message;
        private String actionLabel;
        private String target;
        private String params;

        public DashboardAlert() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public Object getCount() { return count; }
        public void setCount(Object count) { this.count = count; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getActionLabel() { return actionLabel; }
        public void setActionLabel(String actionLabel) { this.actionLabel = actionLabel; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getParams() { return params; }
        public void setParams(String params) { this.params = params; }
    }

    public static class DashboardProfitSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private ProfitData week;
        private ProfitData month;
        private ProfitData year;

        public DashboardProfitSummary() {}

        public static class ProfitData implements Serializable {
            private static final long serialVersionUID = 1L;
            private double revenue;
            private double grossProfit;
            private double expenses;
            private double netProfit;
            private double margin;

            private String startDate;
            private String endDate;

            public ProfitData() {}

            public double getRevenue() { return revenue; }
            public void setRevenue(double revenue) { this.revenue = revenue; }
            public double getGrossProfit() { return grossProfit; }
            public void setGrossProfit(double grossProfit) { this.grossProfit = grossProfit; }
            public double getExpenses() { return expenses; }
            public void setExpenses(double expenses) { this.expenses = expenses; }
            public double getNetProfit() { return netProfit; }
            public void setNetProfit(double netProfit) { this.netProfit = netProfit; }
            public double getMargin() { return margin; }
            public void setMargin(double margin) { this.margin = margin; }

            public String getStartDate() { return startDate; }
            public void setStartDate(String startDate) { this.startDate = startDate; }
            public String getEndDate() { return endDate; }
            public void setEndDate(String endDate) { this.endDate = endDate; }
        }

        public ProfitData getWeek() { return week; }
        public void setWeek(ProfitData week) { this.week = week; }
        public ProfitData getMonth() { return month; }
        public void setMonth(ProfitData month) { this.month = month; }
        public ProfitData getYear() { return year; }
        public void setYear(ProfitData year) { this.year = year; }
    }

    public static class DashboardInventoryHealth implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double inventoryValue;
        private Double stockAvailabilityPct;
        private Double deadStockPct;
        private Double turnoverRate;
        private Double totalItems;
        private Integer newItemsCount;
        private Integer recentlySoldItemsCount;
        private Integer recentlyMovedItemsCount;

        public DashboardInventoryHealth() {}

        public Double getInventoryValue() { return inventoryValue; }
        public void setInventoryValue(Double inventoryValue) { this.inventoryValue = inventoryValue; }
        public Double getStockAvailabilityPct() { return stockAvailabilityPct; }
        public void setStockAvailabilityPct(Double stockAvailabilityPct) { this.stockAvailabilityPct = stockAvailabilityPct; }
        public Double getDeadStockPct() { return deadStockPct; }
        public void setDeadStockPct(Double deadStockPct) { this.deadStockPct = deadStockPct; }
        public Double getTurnoverRate() { return turnoverRate; }
        public void setTurnoverRate(Double turnoverRate) { this.turnoverRate = turnoverRate; }
        public Double getTotalItems() { return totalItems; }
        public void setTotalItems(Double totalItems) { this.totalItems = totalItems; }
        public Integer getNewItemsCount() { return newItemsCount; }
        public void setNewItemsCount(Integer newItemsCount) { this.newItemsCount = newItemsCount; }
        public Integer getRecentlySoldItemsCount() { return recentlySoldItemsCount; }
        public void setRecentlySoldItemsCount(Integer recentlySoldItemsCount) { this.recentlySoldItemsCount = recentlySoldItemsCount; }
        public Integer getRecentlyMovedItemsCount() { return recentlyMovedItemsCount; }
        public void setRecentlyMovedItemsCount(Integer recentlyMovedItemsCount) { this.recentlyMovedItemsCount = recentlyMovedItemsCount; }
    }

    public static class DashboardCharts implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<Object> salesTrend = new ArrayList<>();
        private List<Object> profitTrend = new ArrayList<>();
        private List<Object> expenseTrend = new ArrayList<>();
        private List<Object> dailyPerformance = new ArrayList<>();

        public DashboardCharts() {}

        public List<Object> getSalesTrend() { return salesTrend; }
        public void setSalesTrend(List<Object> salesTrend) { this.salesTrend = salesTrend; }
        public List<Object> getProfitTrend() { return profitTrend; }
        public void setProfitTrend(List<Object> profitTrend) { this.profitTrend = profitTrend; }
        public List<Object> getExpenseTrend() { return expenseTrend; }
        public void setExpenseTrend(List<Object> expenseTrend) { this.expenseTrend = expenseTrend; }
        public List<Object> getDailyPerformance() { return dailyPerformance; }
        public void setDailyPerformance(List<Object> dailyPerformance) { this.dailyPerformance = dailyPerformance; }
    }

    public static class DashboardTopPerformers implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<Object> products = new ArrayList<>();
        private List<Object> customers = new ArrayList<>();
        private List<Object> staff = new ArrayList<>();

        public DashboardTopPerformers() {}

        public List<Object> getProducts() { return products; }
        public void setProducts(List<Object> products) { this.products = products; }
        public List<Object> getCustomers() { return customers; }
        public void setCustomers(List<Object> customers) { this.customers = customers; }
        public List<Object> getStaff() { return staff; }
        public void setStaff(List<Object> staff) { this.staff = staff; }
    }

    public static class DashboardWarning implements Serializable {
        private static final long serialVersionUID = 1L;
        private String section;
        private String message;

        public DashboardWarning() {}
        public DashboardWarning(String section, String message) {
            this.section = section;
            this.message = message;
        }

        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
