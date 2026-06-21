package com.example.valueinsoftbackend.Model.Response;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CompanyDashboardSummaryResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private DashboardSummaryResponse.DashboardContext context;
    private int loadedBranches;
    private int failedBranches;
    private int branchCount;
    private DashboardSummaryResponse.DashboardKpis kpis;
    private DashboardSummaryResponse.DashboardCharts charts;
    private DashboardSummaryResponse.DashboardInventoryHealth inventoryHealth;
    private DashboardSummaryResponse.DashboardProfitSummary profitSummary;
    private List<DashboardSummaryResponse.CategoryPerformance> topCategories = new ArrayList<>();
    private List<CompanyBranchSummary> branches = new ArrayList<>();
    private List<DashboardSummaryResponse.DashboardWarning> warnings = new ArrayList<>();

    public DashboardSummaryResponse.DashboardContext getContext() {
        return context;
    }

    public void setContext(DashboardSummaryResponse.DashboardContext context) {
        this.context = context;
    }

    public int getLoadedBranches() {
        return loadedBranches;
    }

    public void setLoadedBranches(int loadedBranches) {
        this.loadedBranches = loadedBranches;
    }

    public int getFailedBranches() {
        return failedBranches;
    }

    public void setFailedBranches(int failedBranches) {
        this.failedBranches = failedBranches;
    }

    public int getBranchCount() {
        return branchCount;
    }

    public void setBranchCount(int branchCount) {
        this.branchCount = branchCount;
    }

    public DashboardSummaryResponse.DashboardKpis getKpis() {
        return kpis;
    }

    public void setKpis(DashboardSummaryResponse.DashboardKpis kpis) {
        this.kpis = kpis;
    }

    public DashboardSummaryResponse.DashboardCharts getCharts() {
        return charts;
    }

    public void setCharts(DashboardSummaryResponse.DashboardCharts charts) {
        this.charts = charts;
    }

    public DashboardSummaryResponse.DashboardInventoryHealth getInventoryHealth() {
        return inventoryHealth;
    }

    public void setInventoryHealth(DashboardSummaryResponse.DashboardInventoryHealth inventoryHealth) {
        this.inventoryHealth = inventoryHealth;
    }

    public DashboardSummaryResponse.DashboardProfitSummary getProfitSummary() {
        return profitSummary;
    }

    public void setProfitSummary(DashboardSummaryResponse.DashboardProfitSummary profitSummary) {
        this.profitSummary = profitSummary;
    }

    public List<DashboardSummaryResponse.CategoryPerformance> getTopCategories() {
        return topCategories;
    }

    public void setTopCategories(List<DashboardSummaryResponse.CategoryPerformance> topCategories) {
        this.topCategories = topCategories;
    }

    public List<CompanyBranchSummary> getBranches() {
        return branches;
    }

    public void setBranches(List<CompanyBranchSummary> branches) {
        this.branches = branches;
    }

    public List<DashboardSummaryResponse.DashboardWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<DashboardSummaryResponse.DashboardWarning> warnings) {
        this.warnings = warnings;
    }

    public static class CompanyBranchSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        private BranchInfo branch;
        private boolean loaded;
        private double sales;
        private double grossProfit;
        private int orders;
        private double avgOrderValue;
        private int newClients;
        private int alerts;

        public BranchInfo getBranch() {
            return branch;
        }

        public void setBranch(BranchInfo branch) {
            this.branch = branch;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public void setLoaded(boolean loaded) {
            this.loaded = loaded;
        }

        public double getSales() {
            return sales;
        }

        public void setSales(double sales) {
            this.sales = sales;
        }

        public double getGrossProfit() {
            return grossProfit;
        }

        public void setGrossProfit(double grossProfit) {
            this.grossProfit = grossProfit;
        }

        public int getOrders() {
            return orders;
        }

        public void setOrders(int orders) {
            this.orders = orders;
        }

        public double getAvgOrderValue() {
            return avgOrderValue;
        }

        public void setAvgOrderValue(double avgOrderValue) {
            this.avgOrderValue = avgOrderValue;
        }

        public int getNewClients() {
            return newClients;
        }

        public void setNewClients(int newClients) {
            this.newClients = newClients;
        }

        public int getAlerts() {
            return alerts;
        }

        public void setAlerts(int alerts) {
            this.alerts = alerts;
        }
    }

    public static class BranchInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private int branchID;
        private String branchName;

        public int getBranchID() {
            return branchID;
        }

        public void setBranchID(int branchID) {
            this.branchID = branchID;
        }

        public String getBranchName() {
            return branchName;
        }

        public void setBranchName(String branchName) {
            this.branchName = branchName;
        }
    }
}
