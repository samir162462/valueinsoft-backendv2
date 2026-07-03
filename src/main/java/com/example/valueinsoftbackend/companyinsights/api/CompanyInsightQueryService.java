package com.example.valueinsoftbackend.companyinsights.api;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.companyinsights.api.dto.CompanyInsightSettingsDto;
import com.example.valueinsoftbackend.companyinsights.api.dto.CompanyInsightSummaryDto;
import com.example.valueinsoftbackend.companyinsights.api.dto.InsightDto;
import com.example.valueinsoftbackend.companyinsights.api.dto.InsightPageDto;
import com.example.valueinsoftbackend.companyinsights.api.dto.InsightStatusRequest;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import com.example.valueinsoftbackend.companyinsights.backfill.CompanyInsightBackfillService;
import com.example.valueinsoftbackend.companyinsights.engine.CompanyInsightEngineService;
import com.example.valueinsoftbackend.companyinsights.kpi.CompanyKpiAggregationService;
import com.example.valueinsoftbackend.companyinsights.kpi.CompanyKpiRepository;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightAuditService;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightQueryRepository;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightRepository;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightSettingsService;
import com.example.valueinsoftbackend.companyinsights.security.CompanyInsightSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service for the Company Smart Insights Admin API. Enforces company-admin
 * authorization and tenant isolation, then delegates to the read/write repositories.
 */
@Service
public class CompanyInsightQueryService {

    private static final Set<String> ALLOWED_STATUS = Set.of("SEEN", "DISMISSED", "RESOLVED");

    private final CompanyInsightSecurityService security;
    private final CompanyInsightQueryRepository queryRepository;
    private final CompanyInsightRepository insightRepository;
    private final CompanyInsightSettingsService settingsService;
    private final CompanyKpiRepository kpiRepository;
    private final CompanyInsightAuditService auditService;
    private final CompanyInsightEngineService engineService;
    private final CompanyKpiAggregationService aggregationService;
    private final CompanyInsightBackfillService backfillService;

    public CompanyInsightQueryService(CompanyInsightSecurityService security,
                                      CompanyInsightQueryRepository queryRepository,
                                      CompanyInsightRepository insightRepository,
                                      CompanyInsightSettingsService settingsService,
                                      CompanyKpiRepository kpiRepository,
                                      CompanyInsightAuditService auditService,
                                      CompanyInsightEngineService engineService,
                                      CompanyKpiAggregationService aggregationService,
                                      CompanyInsightBackfillService backfillService) {
        this.security = security;
        this.queryRepository = queryRepository;
        this.insightRepository = insightRepository;
        this.settingsService = settingsService;
        this.kpiRepository = kpiRepository;
        this.auditService = auditService;
        this.engineService = engineService;
        this.aggregationService = aggregationService;
        this.backfillService = backfillService;
    }

    public long startBackfill(Principal principal, LocalDate from, LocalDate to) {
        long startedAt = System.nanoTime();
        AiSecurityContext ctx = security.authorizeAdmin(principal);
        LocalDate toDate = to == null ? LocalDate.now() : to;
        LocalDate fromDate = from == null ? toDate.minusMonths(3) : from;
        if (fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPANY_INSIGHT_INVALID_FILTER", "from must be on or before to");
        }
        long id = backfillService.createBackfill(ctx.companyId(), fromDate, toDate, ctx.userId());
        auditService.log(ctx.companyId(), null, ctx.userId(), "BACKFILL_TRIGGERED",
                "{\"from\":\"" + fromDate + "\",\"to\":\"" + toDate + "\"}", true, elapsedMs(startedAt));
        return id;
    }

    public java.util.Map<String, Object> backfillStatus(Principal principal, long backfillId) {
        AiSecurityContext ctx = security.authorizeView(principal);
        return backfillService.status(ctx.companyId(), backfillId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_INSIGHT_BACKFILL_NOT_FOUND", "Backfill not found"));
    }

    public InsightPageDto list(Principal principal, String severity, String category, String insightType,
                               Long branchId, String status, String role, LocalDate from, LocalDate to,
                               int page, int pageSize) {
        AiSecurityContext ctx = security.authorizeView(principal);
        List<String> statuses = status == null || status.isBlank() ? null : List.of(status);
        CompanyInsightQueryRepository.InsightQuery query = new CompanyInsightQueryRepository.InsightQuery(
                ctx.companyId(), statuses, role, severity, category, insightType, branchId, from, to, page, pageSize);
        long total = queryRepository.count(query);
        List<InsightDto> items = queryRepository.list(query);
        int effectivePageSize = Math.min(Math.max(1, pageSize), 100);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) effectivePageSize);
        return new InsightPageDto(items, Math.max(0, page), effectivePageSize, total, totalPages);
    }

    public InsightDto detail(Principal principal, long id) {
        AiSecurityContext ctx = security.authorizeView(principal);
        return queryRepository.findById(ctx.companyId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_INSIGHT_NOT_FOUND", "Insight not found"));
    }

    public CompanyInsightSummaryDto summary(Principal principal, LocalDate from, LocalDate to) {
        AiSecurityContext ctx = security.authorizeView(principal);
        LocalDate toDate = to == null ? LocalDate.now() : to;
        LocalDate fromDate = from == null ? toDate.minusDays(29) : from;
        if (fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPANY_INSIGHT_INVALID_FILTER", "from must be on or before to");
        }

        List<CompanyKpiRepository.CompanyDailyKpiRow> rows =
                kpiRepository.findCompanyDailyRange(ctx.companyId(), fromDate, toDate);

        double sales = 0;
        double grossProfit = 0;
        long orders = 0;
        int branchCount = 0;
        List<CompanyInsightSummaryDto.TrendPoint> trend = new java.util.ArrayList<>();
        for (CompanyKpiRepository.CompanyDailyKpiRow row : rows) {
            sales += row.salesAmount();
            grossProfit += row.grossProfitAmount();
            orders += row.ordersCount();
            branchCount = Math.max(branchCount, row.branchCount());
            trend.add(new CompanyInsightSummaryDto.TrendPoint(
                    row.businessDate() == null ? null : row.businessDate().toString(),
                    round2(row.salesAmount()),
                    round2(row.grossProfitAmount()),
                    row.grossMarginPct()
            ));
        }
        double margin = sales > 0 ? Math.round(grossProfit / sales * 1000.0) / 10.0 : 0.0;

        Map<String, Long> severityCounts = queryRepository.activeSeverityCounts(ctx.companyId());
        long critical = severityCounts.getOrDefault("CRITICAL", 0L);
        long warning = severityCounts.getOrDefault("WARNING", 0L);
        long info = severityCounts.getOrDefault("INFO", 0L);

        return new CompanyInsightSummaryDto(
                fromDate.toString(),
                toDate.toString(),
                round2(sales),
                round2(grossProfit),
                margin,
                orders,
                branchCount,
                new CompanyInsightSummaryDto.AlertCounts(critical, warning, info, critical + warning + info),
                trend
        );
    }

    public InsightDto updateStatus(Principal principal, long id, InsightStatusRequest request) {
        long startedAt = System.nanoTime();
        AiSecurityContext ctx = security.authorizeView(principal);
        String status = request == null ? null : request.status();
        if (status == null || !ALLOWED_STATUS.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPANY_INSIGHT_INVALID_STATUS",
                    "status must be one of SEEN, DISMISSED, RESOLVED");
        }
        // Ensure the insight belongs to the caller's company before mutating.
        queryRepository.findById(ctx.companyId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_INSIGHT_NOT_FOUND", "Insight not found"));

        CompanyInsightThresholds thresholds = settingsService.resolve(ctx.companyId());
        insightRepository.applyStatus(ctx.companyId(), id, status, thresholds.insightCooldownHours());
        auditService.log(ctx.companyId(), id, ctx.userId(), status,
                request.note() == null ? null : "{\"note\":\"" + escape(request.note()) + "\"}",
                true, elapsedMs(startedAt));
        return detail(principal, id);
    }

    public CompanyInsightSettingsDto getSettings(Principal principal) {
        AiSecurityContext ctx = security.authorizeView(principal);
        return CompanyInsightSettingsDto.from(settingsService.resolve(ctx.companyId()));
    }

    public CompanyInsightSettingsDto updateSettings(Principal principal, CompanyInsightSettingsDto dto) {
        long startedAt = System.nanoTime();
        AiSecurityContext ctx = security.authorizeConfigure(principal);
        CompanyInsightThresholds saved = settingsService.save(dto.toThresholds(ctx.companyId()));
        auditService.log(ctx.companyId(), null, ctx.userId(), "SETTINGS_UPDATED", null, true, elapsedMs(startedAt));
        return CompanyInsightSettingsDto.from(saved);
    }

    /**
     * Manual populate + recalculate for one company: aggregates a trailing window of trusted
     * KPI snapshots (so weekly/branch comparisons have a baseline), then runs the deterministic
     * insight engine. Synchronous and admin-gated; intended for on-demand admin use, not the
     * dashboard read path. For large tenants, prefer the async backfill.
     */
    public int recalculate(Principal principal, LocalDate asOfDate) {
        long startedAt = System.nanoTime();
        AiSecurityContext ctx = security.authorizeAdmin(principal);
        int companyId = Math.toIntExact(ctx.companyId());
        LocalDate target = asOfDate == null ? LocalDate.now() : asOfDate;

        // Populate ~5 weeks of snapshots so current-vs-previous week and branch baselines exist.
        aggregationService.aggregateCompanyRange(companyId, target.minusDays(34), target);

        int persisted = engineService.generateForCompany(companyId, target);
        auditService.log(ctx.companyId(), null, ctx.userId(), "RECALC_TRIGGERED",
                "{\"asOf\":\"" + target + "\"}", true, elapsedMs(startedAt));
        return persisted;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
