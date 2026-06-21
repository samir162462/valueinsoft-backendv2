package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.CompanyDashboardSummaryRequest;
import com.example.valueinsoftbackend.Model.Request.DashboardSummaryRequest;
import com.example.valueinsoftbackend.Model.Response.CompanyDashboardSummaryResponse;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.Config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardChartProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardInventoryProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardKpiProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardTopPerformerProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardProfitProvider;

@Service
@Slf4j
public class DashboardSummaryService {

    private static final int DASHBOARD_EXECUTOR_THREADS = Math.min(
            4,
            Math.max(2, Runtime.getRuntime().availableProcessors())
    );
    private static final AtomicInteger DASHBOARD_THREAD_SEQUENCE = new AtomicInteger(1);

    private final DbBranch dbBranch;
    private final DashboardKpiProvider kpiProvider;
    private final DashboardChartProvider chartProvider;
    private final DashboardTopPerformerProvider topPerformerProvider;
    private final DashboardInventoryProvider inventoryProvider;
    private final DashboardProfitProvider profitProvider;
    private final ExecutorService dashboardExecutor = Executors.newFixedThreadPool(
            DASHBOARD_EXECUTOR_THREADS,
            runnable -> {
                Thread thread = new Thread(runnable, "dashboard-summary-" + DASHBOARD_THREAD_SEQUENCE.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
    );

    public DashboardSummaryService(DbBranch dbBranch, 
                                   DashboardKpiProvider kpiProvider,
                                   DashboardChartProvider chartProvider,
                                   DashboardTopPerformerProvider topPerformerProvider,
                                   DashboardInventoryProvider inventoryProvider,
                                   DashboardProfitProvider profitProvider) {
        this.dbBranch = dbBranch;
        this.kpiProvider = kpiProvider;
        this.chartProvider = chartProvider;
        this.topPerformerProvider = topPerformerProvider;
        this.inventoryProvider = inventoryProvider;
        this.profitProvider = profitProvider;
    }

    @PreDestroy
    public void shutdownDashboardExecutor() {
        dashboardExecutor.shutdownNow();
    }

    @Cacheable(
            cacheNames = CacheConfig.DASHBOARD_BRANCH_SUMMARY,
            key = "#companyId + ':' + #request.branchId + ':' + #request.date + ':' + (#request.period == null ? 'TODAY' : #request.period)"
    )
    public DashboardSummaryResponse getBranchSummary(Integer companyId, DashboardSummaryRequest request) {
        
        // Validate Branch belongs to Company
        Branch branch = dbBranch.getBranchById(request.getBranchId());
        if (branch == null || branch.getBranchOfCompanyId() != companyId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED", "Branch not found or access denied");
        }

        DashboardSummaryResponse response = new DashboardSummaryResponse();

        // 1. Context
        DashboardSummaryResponse.DashboardContext context = new DashboardSummaryResponse.DashboardContext();
        context.setBranchId(branch.getBranchID());
        context.setBranchName(branch.getBranchName());
        context.setDate(request.getDate());
        context.setPeriod(request.getPeriod() != null ? request.getPeriod() : "TODAY");
        context.setCurrency("EGP"); // In real app, this should come from Branch Settings
        context.setGeneratedAt(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        response.setContext(context);

        // 2. Fetch KPIs asynchronously
        CompletableFuture<DashboardKpiProvider.KpiPackage> kpiPackageFuture = CompletableFuture.supplyAsync(
                () -> kpiProvider.getKpis(companyId, request.getBranchId(), request.getDate()),
                dashboardExecutor
        ).orTimeout(10, TimeUnit.SECONDS).exceptionally(ex -> {
            log.warn("KPI fetch failed for branch {}", request.getBranchId(), ex);
            response.getSectionStatus().put("kpis", "error");
            return null;
        });

        // 3. Fetch Charts asynchronously
        CompletableFuture<DashboardSummaryResponse.DashboardCharts> chartsFuture = CompletableFuture.supplyAsync(
                () -> chartProvider.getCharts(companyId, request.getBranchId(), request.getDate()),
                dashboardExecutor
        ).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            log.warn("Charts fetch failed", ex);
            response.getSectionStatus().put("charts", "error");
            return null;
        });

        // 4. Fetch Top Performers asynchronously
        CompletableFuture<DashboardSummaryResponse.DashboardTopPerformers> topPerformersFuture = CompletableFuture.supplyAsync(
                () -> topPerformerProvider.getTopPerformers(companyId, request.getBranchId(), request.getDate()),
                dashboardExecutor
        ).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            log.warn("Top performers fetch failed", ex);
            response.getSectionStatus().put("topPerformers", "error");
            return null;
        });

        // 5. Fetch Inventory Alerts asynchronously
        CompletableFuture<List<DashboardSummaryResponse.DashboardAlert>> alertsFuture = CompletableFuture.supplyAsync(
                () -> inventoryProvider.getInventoryAlerts(companyId, request.getBranchId()),
                dashboardExecutor
        ).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            log.warn("Inventory alerts fetch failed", ex);
            return new ArrayList<>();
        });

        // Await KPI completion
        DashboardKpiProvider.KpiPackage kpiPackage = kpiPackageFuture.join();
        if (kpiPackage != null) {
            response.setKpis(kpiPackage.kpis);
            response.setComparisons(kpiPackage.comparisons);
            response.getSectionStatus().put("kpis", "success");
            response.getSectionStatus().put("comparisons", "success");
        }

        // Await Charts completion
        DashboardSummaryResponse.DashboardCharts charts = chartsFuture.join();
        if (charts != null) {
            response.setCharts(charts);
            response.getSectionStatus().put("charts", "success");
        }

        // Await Top Performers completion
        DashboardSummaryResponse.DashboardTopPerformers topPerformers = topPerformersFuture.join();
        if (topPerformers != null) {
            response.setTopPerformers(topPerformers);
            response.getSectionStatus().put("topPerformers", "success");
        }

        // Await Alerts completion
        List<DashboardSummaryResponse.DashboardAlert> alerts = alertsFuture.join();
        response.setAlerts(alerts != null ? alerts : new ArrayList<>());
        response.getSectionStatus().put("alerts", alerts != null ? "success" : "error");

        // 4. Profit Summary (Finance Snapshot)
        try {
            LocalDate baseDate = LocalDate.parse(request.getDate());
            response.setProfitSummary(profitProvider.getProfitSummary(companyId, request.getBranchId(), baseDate));
            response.getSectionStatus().put("profitSummary", "success");
        } catch (Exception e) {
            log.error("Error providing profit summary: {}", e.getMessage());
            response.getSectionStatus().put("profitSummary", "error");
            response.getWarnings().add(new DashboardSummaryResponse.DashboardWarning("profitSummary", "Failed to load financial data"));
        }

        // 5. Inventory Health
        try {
            response.setInventoryHealth(inventoryProvider.getInventoryHealth(companyId, request.getBranchId()));
            response.getSectionStatus().put("inventoryHealth", "success");
        } catch (Exception e) {
            log.error("Error providing inventory health: {}", e.getMessage());
            response.getSectionStatus().put("inventoryHealth", "error");
        }


        return response;
    }

    @Cacheable(
            cacheNames = CacheConfig.DASHBOARD_COMPANY_SUMMARY,
            key = "#companyId + ':' + #request.date + ':' + (#request.period == null ? 'TODAY' : #request.period)"
    )
    public CompanyDashboardSummaryResponse getCompanySummary(Integer companyId, CompanyDashboardSummaryRequest request) {
        List<Branch> branches = dbBranch.getBranchByCompanyId(companyId);
        CompanyDashboardSummaryResponse response = new CompanyDashboardSummaryResponse();

        DashboardSummaryResponse.DashboardContext context = new DashboardSummaryResponse.DashboardContext();
        context.setDate(request.getDate());
        context.setPeriod(request.getPeriod() != null ? request.getPeriod() : "TODAY");
        context.setCurrency("EGP");
        context.setGeneratedAt(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        context.setBranchName("All branches");
        response.setContext(context);
        response.setBranchCount(branches.size());

        AggregationState state = new AggregationState();
        LocalDate baseDate = LocalDate.parse(request.getDate());

        for (Branch branch : branches) {
            DashboardSummaryRequest branchRequest = new DashboardSummaryRequest();
            branchRequest.setBranchId(branch.getBranchID());
            branchRequest.setDate(request.getDate());
            branchRequest.setPeriod(request.getPeriod() != null ? request.getPeriod() : "TODAY");

            try {
                DashboardSummaryResponse branchSummary = getBranchSummary(companyId, branchRequest);
                state.loadedBranches++;
                state.addBranchSummary(branch, branchSummary);
            } catch (Exception ex) {
                state.failedBranches++;
                response.getWarnings().add(new DashboardSummaryResponse.DashboardWarning(
                        "branch:" + branch.getBranchID(),
                        "Failed to load dashboard summary for " + branch.getBranchName()
                ));
                log.warn("Company dashboard branch summary failed companyId={} branchId={}",
                        companyId, branch.getBranchID(), ex);
                state.addFailedBranch(branch);
            }
        }

        response.setLoadedBranches(state.loadedBranches);
        response.setFailedBranches(state.failedBranches);
        response.setKpis(state.toKpis());
        response.setCharts(state.toCharts());
        response.setInventoryHealth(state.toInventoryHealth());
        response.setProfitSummary(state.toProfitSummary(baseDate));
        response.setTopCategories(state.toTopCategories());
        response.setBranches(state.toBranches());

        return response;
    }

    private static final class AggregationState {
        private static final List<String> DAY_ORDER = List.of("SAT", "SUN", "MON", "TUE", "WED", "THU", "FRI");
        private static final List<String> MONTH_ORDER = List.of("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");

        private int loadedBranches;
        private int failedBranches;
        private double todaySales;
        private double grossProfit;
        private int ordersCount;
        private int newClients;
        private double cashOnHand;
        private double inventoryValue;
        private double totalItems;
        private int newItemsCount;
        private int recentlySoldItemsCount;
        private int recentlyMovedItemsCount;
        private double stockAvailabilityPct;
        private double deadStockPct;
        private double turnoverRate;
        private final Map<String, Double> dailyPerformance = orderedMap(DAY_ORDER);
        private final Map<String, Double> clientTrend = orderedMap(MONTH_ORDER);
        private final Map<String, Double> paymentMethods = new LinkedHashMap<>();
        private final Map<String, DashboardSummaryResponse.CategoryPerformance> categories = new HashMap<>();
        private final DashboardSummaryResponse.DashboardProfitSummary profitSummary = new DashboardSummaryResponse.DashboardProfitSummary();
        private final List<CompanyDashboardSummaryResponse.CompanyBranchSummary> branches = new ArrayList<>();

        private static Map<String, Double> orderedMap(List<String> keys) {
            Map<String, Double> map = new LinkedHashMap<>();
            for (String key : keys) {
                map.put(key, 0.0);
            }
            return map;
        }

        private void addBranchSummary(Branch branch, DashboardSummaryResponse summary) {
            DashboardSummaryResponse.DashboardKpis kpis = summary.getKpis();
            double branchSales = number(kpis == null ? null : kpis.getTodaySales());
            double branchProfit = number(kpis == null ? null : kpis.getGrossProfit());
            int branchOrders = integer(kpis == null ? null : kpis.getOrdersCount());
            int branchClients = integer(kpis == null ? null : kpis.getNewClients());

            todaySales += branchSales;
            grossProfit += branchProfit;
            ordersCount += branchOrders;
            newClients += branchClients;
            cashOnHand += number(kpis == null ? null : kpis.getCashOnHand());

            DashboardSummaryResponse.DashboardInventoryHealth inventory = summary.getInventoryHealth();
            if (inventory != null) {
                inventoryValue += number(inventory.getInventoryValue());
                totalItems += number(inventory.getTotalItems());
                newItemsCount += integer(inventory.getNewItemsCount());
                recentlySoldItemsCount += integer(inventory.getRecentlySoldItemsCount());
                recentlyMovedItemsCount += integer(inventory.getRecentlyMovedItemsCount());
                stockAvailabilityPct += number(inventory.getStockAvailabilityPct());
                deadStockPct += number(inventory.getDeadStockPct());
                turnoverRate += number(inventory.getTurnoverRate());
            }

            DashboardSummaryResponse.DashboardCharts charts = summary.getCharts();
            if (charts != null) {
                mergeMetricRows(dailyPerformance, charts.getDailyPerformance(), "day");
                mergeMetricRows(clientTrend, charts.getClientTrend(), "month");
                mergeMetricRows(paymentMethods, charts.getPaymentMethods(), "type");
            }

            DashboardSummaryResponse.DashboardTopPerformers topPerformers = summary.getTopPerformers();
            if (topPerformers != null && topPerformers.getCategories() != null) {
                mergeCategories(topPerformers.getCategories());
            }

            addProfitSummary(summary.getProfitSummary());

            CompanyDashboardSummaryResponse.CompanyBranchSummary branchRow = baseBranchRow(branch);
            branchRow.setLoaded(true);
            branchRow.setSales(branchSales);
            branchRow.setGrossProfit(branchProfit);
            branchRow.setOrders(branchOrders);
            branchRow.setAvgOrderValue(branchOrders > 0 ? branchSales / branchOrders : 0.0);
            branchRow.setNewClients(branchClients);
            branchRow.setAlerts(summary.getAlerts() == null ? 0 : summary.getAlerts().size());
            branches.add(branchRow);
        }

        private void addFailedBranch(Branch branch) {
            branches.add(baseBranchRow(branch));
        }

        private CompanyDashboardSummaryResponse.CompanyBranchSummary baseBranchRow(Branch branch) {
            CompanyDashboardSummaryResponse.BranchInfo branchInfo = new CompanyDashboardSummaryResponse.BranchInfo();
            branchInfo.setBranchID(branch.getBranchID());
            branchInfo.setBranchName(branch.getBranchName());

            CompanyDashboardSummaryResponse.CompanyBranchSummary branchRow = new CompanyDashboardSummaryResponse.CompanyBranchSummary();
            branchRow.setBranch(branchInfo);
            branchRow.setLoaded(false);
            return branchRow;
        }

        private DashboardSummaryResponse.DashboardKpis toKpis() {
            DashboardSummaryResponse.DashboardKpis kpis = new DashboardSummaryResponse.DashboardKpis();
            kpis.setTodaySales(todaySales);
            kpis.setGrossProfit(grossProfit);
            kpis.setOrdersCount(ordersCount);
            kpis.setNewClients(newClients);
            kpis.setCashOnHand(cashOnHand);
            kpis.setAvgOrderValue(ordersCount > 0 ? todaySales / ordersCount : 0.0);
            return kpis;
        }

        private DashboardSummaryResponse.DashboardCharts toCharts() {
            DashboardSummaryResponse.DashboardCharts charts = new DashboardSummaryResponse.DashboardCharts();
            charts.setDailyPerformance(toPointRows(dailyPerformance, "day"));
            charts.setClientTrend(toPointRows(clientTrend, "month"));
            charts.setPaymentMethods(toPointRows(paymentMethods, "type"));
            return charts;
        }

        private DashboardSummaryResponse.DashboardInventoryHealth toInventoryHealth() {
            DashboardSummaryResponse.DashboardInventoryHealth inventory = new DashboardSummaryResponse.DashboardInventoryHealth();
            inventory.setInventoryValue(inventoryValue);
            inventory.setTotalItems(totalItems);
            inventory.setNewItemsCount(newItemsCount);
            inventory.setRecentlySoldItemsCount(recentlySoldItemsCount);
            inventory.setRecentlyMovedItemsCount(recentlyMovedItemsCount);

            int divisor = Math.max(loadedBranches, 1);
            inventory.setStockAvailabilityPct(roundOne(stockAvailabilityPct / divisor));
            inventory.setDeadStockPct(roundOne(deadStockPct / divisor));
            inventory.setTurnoverRate(roundOne(turnoverRate / divisor));
            return inventory;
        }

        private DashboardSummaryResponse.DashboardProfitSummary toProfitSummary(LocalDate baseDate) {
            ensureProfitDateRanges(profitSummary.getWeek(), baseDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY)), baseDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY)).plusDays(7));
            ensureProfitDateRanges(profitSummary.getMonth(), baseDate.with(java.time.temporal.TemporalAdjusters.firstDayOfMonth()), baseDate.with(java.time.temporal.TemporalAdjusters.firstDayOfMonth()).plusMonths(1));
            ensureProfitDateRanges(profitSummary.getYear(), baseDate.with(java.time.temporal.TemporalAdjusters.firstDayOfYear()), baseDate.with(java.time.temporal.TemporalAdjusters.firstDayOfYear()).plusYears(1));
            recalculateMargin(profitSummary.getWeek());
            recalculateMargin(profitSummary.getMonth());
            recalculateMargin(profitSummary.getYear());
            return profitSummary;
        }

        private List<DashboardSummaryResponse.CategoryPerformance> toTopCategories() {
            return categories.values().stream()
                    .sorted(Comparator.comparing((DashboardSummaryResponse.CategoryPerformance item) -> number(item.getTotalSales())).reversed())
                    .limit(8)
                    .toList();
        }

        private List<CompanyDashboardSummaryResponse.CompanyBranchSummary> toBranches() {
            branches.sort(Comparator.comparing(CompanyDashboardSummaryResponse.CompanyBranchSummary::getSales).reversed());
            return branches;
        }

        private void mergeMetricRows(Map<String, Double> target, List<Object> rows, String labelKey) {
            if (rows == null) return;
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map)) continue;
                String key = string(map.get(labelKey));
                if (key == null || key.isBlank()) continue;
                String normalizedKey = key.trim().toUpperCase();
                target.put(normalizedKey, target.getOrDefault(normalizedKey, 0.0) + number(valueFromMap(map)));
            }
        }

        private Object valueFromMap(Map<?, ?> map) {
            Object value = map.get("value");
            if (value == null) value = map.get("total");
            if (value == null) value = map.get("totalSales");
            if (value == null) value = map.get("total_amount");
            if (value == null) value = map.get("count");
            return value;
        }

        private List<Object> toPointRows(Map<String, Double> source, String labelKey) {
            List<Object> rows = new ArrayList<>();
            source.forEach((key, value) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put(labelKey, key);
                row.put("value", value);
                rows.add(row);
            });
            return rows;
        }

        private void mergeCategories(List<DashboardSummaryResponse.CategoryPerformance> source) {
            for (DashboardSummaryResponse.CategoryPerformance item : source) {
                String name = item.getName() == null || item.getName().isBlank() ? "Other" : item.getName();
                DashboardSummaryResponse.CategoryPerformance target = categories.computeIfAbsent(name, key -> {
                    DashboardSummaryResponse.CategoryPerformance category = new DashboardSummaryResponse.CategoryPerformance();
                    category.setName(key);
                    category.setTotalSales(0.0);
                    category.setSubCategories(new ArrayList<>());
                    return category;
                });
                target.setTotalSales(number(target.getTotalSales()) + number(item.getTotalSales()));
            }
        }

        private void addProfitSummary(DashboardSummaryResponse.DashboardProfitSummary source) {
            if (source == null) return;
            profitSummary.setWeek(addProfitData(profitSummary.getWeek(), source.getWeek()));
            profitSummary.setMonth(addProfitData(profitSummary.getMonth(), source.getMonth()));
            profitSummary.setYear(addProfitData(profitSummary.getYear(), source.getYear()));
        }

        private DashboardSummaryResponse.DashboardProfitSummary.ProfitData addProfitData(
                DashboardSummaryResponse.DashboardProfitSummary.ProfitData current,
                DashboardSummaryResponse.DashboardProfitSummary.ProfitData source
        ) {
            DashboardSummaryResponse.DashboardProfitSummary.ProfitData result =
                    current == null ? new DashboardSummaryResponse.DashboardProfitSummary.ProfitData() : current;
            if (source == null) return result;
            result.setRevenue(result.getRevenue() + source.getRevenue());
            result.setGrossProfit(result.getGrossProfit() + source.getGrossProfit());
            result.setExpenses(result.getExpenses() + source.getExpenses());
            result.setNetProfit(result.getNetProfit() + source.getNetProfit());
            if (result.getStartDate() == null) result.setStartDate(source.getStartDate());
            if (result.getEndDate() == null) result.setEndDate(source.getEndDate());
            return result;
        }

        private void recalculateMargin(DashboardSummaryResponse.DashboardProfitSummary.ProfitData data) {
            if (data == null) return;
            data.setMargin(data.getRevenue() > 0 ? roundOne((data.getGrossProfit() / data.getRevenue()) * 100.0) : 0.0);
        }

        private void ensureProfitDateRanges(DashboardSummaryResponse.DashboardProfitSummary.ProfitData data, LocalDate start, LocalDate endExclusive) {
            if (data == null) return;
            if (data.getStartDate() == null) data.setStartDate(start.toString());
            if (data.getEndDate() == null) data.setEndDate(endExclusive.minusDays(1).toString());
        }

        private double roundOne(double value) {
            return Math.round(value * 10.0) / 10.0;
        }

        private static double number(Object value) {
            if (value instanceof Number number) return number.doubleValue();
            if (value instanceof String text) {
                try {
                    return Double.parseDouble(text.replace(",", "").trim());
                } catch (NumberFormatException ignored) {
                    return 0.0;
                }
            }
            return 0.0;
        }

        private static int integer(Object value) {
            return (int) Math.round(number(value));
        }

        private static String string(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }
}
