package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.DashboardSummaryRequest;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardChartProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardInventoryProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardKpiProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardTopPerformerProvider;
import com.example.valueinsoftbackend.Service.DashboardProviders.DashboardProfitProvider;

@Service
@Slf4j
public class DashboardSummaryService {

    private final DbBranch dbBranch;
    private final DashboardKpiProvider kpiProvider;
    private final DashboardChartProvider chartProvider;
    private final DashboardTopPerformerProvider topPerformerProvider;
    private final DashboardInventoryProvider inventoryProvider;
    private final DashboardProfitProvider profitProvider;
    private final ExecutorService dashboardExecutor = Executors.newFixedThreadPool(10); // Bounded pool

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
            System.err.println("KPI fetch failed for Branch " + request.getBranchId() + ": " + ex.getMessage());
            response.getSectionStatus().put("kpis", "error");
            return null;
        });

        // 3. Fetch Charts asynchronously
        CompletableFuture<DashboardSummaryResponse.DashboardCharts> chartsFuture = CompletableFuture.supplyAsync(
                () -> chartProvider.getCharts(companyId, request.getBranchId(), request.getDate()),
                dashboardExecutor
        ).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            System.err.println("Charts fetch failed: " + ex.getMessage());
            response.getSectionStatus().put("charts", "error");
            return null;
        });

        // 4. Fetch Top Performers asynchronously
        CompletableFuture<DashboardSummaryResponse.DashboardTopPerformers> topPerformersFuture = CompletableFuture.supplyAsync(
                () -> topPerformerProvider.getTopPerformers(companyId, request.getBranchId(), request.getDate()),
                dashboardExecutor
        ).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            System.err.println("Top performers fetch failed: " + ex.getMessage());
            response.getSectionStatus().put("topPerformers", "error");
            return null;
        });

        // 5. Fetch Inventory Alerts asynchronously
        CompletableFuture<List<DashboardSummaryResponse.DashboardAlert>> alertsFuture = CompletableFuture.supplyAsync(
                () -> inventoryProvider.getInventoryAlerts(companyId, request.getBranchId()),
                dashboardExecutor
        ).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            System.err.println("Inventory alerts fetch failed: " + ex.getMessage());
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
}
