package com.example.valueinsoftbackend.Model.Response;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class UserPerformanceResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userName;
    private String period;
    private String periodLabel;
    private String startDate;
    private String endDate;
    private String currency;
    private String generatedAt;
    private PerformanceTotals current;
    private PerformanceTotals previous;
    private Double salesChangePct;
    private Double ordersChangePct;
    private List<PerformancePoint> series = new ArrayList<>();

    public UserPerformanceResponse() {}

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }

    public PerformanceTotals getCurrent() { return current; }
    public void setCurrent(PerformanceTotals current) { this.current = current; }

    public PerformanceTotals getPrevious() { return previous; }
    public void setPrevious(PerformanceTotals previous) { this.previous = previous; }

    public Double getSalesChangePct() { return salesChangePct; }
    public void setSalesChangePct(Double salesChangePct) { this.salesChangePct = salesChangePct; }

    public Double getOrdersChangePct() { return ordersChangePct; }
    public void setOrdersChangePct(Double ordersChangePct) { this.ordersChangePct = ordersChangePct; }

    public List<PerformancePoint> getSeries() { return series; }
    public void setSeries(List<PerformancePoint> series) { this.series = series; }

    public static class PerformanceTotals implements Serializable {
        private static final long serialVersionUID = 1L;

        private double salesTotal;
        private int ordersCount;
        private double avgOrderValue;
        private double incomeTotal;
        private int distinctClients;

        public PerformanceTotals() {}

        public double getSalesTotal() { return salesTotal; }
        public void setSalesTotal(double salesTotal) { this.salesTotal = salesTotal; }

        public int getOrdersCount() { return ordersCount; }
        public void setOrdersCount(int ordersCount) { this.ordersCount = ordersCount; }

        public double getAvgOrderValue() { return avgOrderValue; }
        public void setAvgOrderValue(double avgOrderValue) { this.avgOrderValue = avgOrderValue; }

        public double getIncomeTotal() { return incomeTotal; }
        public void setIncomeTotal(double incomeTotal) { this.incomeTotal = incomeTotal; }

        public int getDistinctClients() { return distinctClients; }
        public void setDistinctClients(int distinctClients) { this.distinctClients = distinctClients; }
    }

    public static class PerformancePoint implements Serializable {
        private static final long serialVersionUID = 1L;

        private String label;
        private double value;
        private int orders;

        public PerformancePoint() {}

        public PerformancePoint(String label, double value, int orders) {
            this.label = label;
            this.value = value;
            this.orders = orders;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public int getOrders() { return orders; }
        public void setOrders(int orders) { this.orders = orders; }
    }
}
