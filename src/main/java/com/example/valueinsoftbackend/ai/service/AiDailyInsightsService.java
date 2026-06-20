package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.DashboardSummaryRequest;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.Service.DashboardSummaryService;
import com.example.valueinsoftbackend.ai.cache.AiInsightCacheService;
import com.example.valueinsoftbackend.ai.dto.AiDailyInsightDto;
import com.example.valueinsoftbackend.ai.dto.AiDailyInsightsRequest;
import com.example.valueinsoftbackend.ai.dto.AiDailyInsightsResponse;
import com.example.valueinsoftbackend.ai.sql.AiSqlAgentService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class AiDailyInsightsService {

    private final AiSecurityContextResolver securityContextResolver;
    private final AiPermissionService permissionService;
    private final AiRateLimitService rateLimitService;
    private final AiCostTrackingService costTrackingService;
    private final AiInsightCacheService insightCacheService;
    private final AiSqlAgentService sqlAgentService;
    private final AiModelClient modelClient;
    private final DashboardSummaryService dashboardSummaryService;
    private final Gson gson;

    public AiDailyInsightsService(AiSecurityContextResolver securityContextResolver,
                                  AiPermissionService permissionService,
                                  AiRateLimitService rateLimitService,
                                  AiCostTrackingService costTrackingService,
                                  AiInsightCacheService insightCacheService,
                                  AiSqlAgentService sqlAgentService,
                                  AiModelClient modelClient,
                                  DashboardSummaryService dashboardSummaryService,
                                  Gson gson) {
        this.securityContextResolver = securityContextResolver;
        this.permissionService = permissionService;
        this.rateLimitService = rateLimitService;
        this.costTrackingService = costTrackingService;
        this.insightCacheService = insightCacheService;
        this.sqlAgentService = sqlAgentService;
        this.modelClient = modelClient;
        this.dashboardSummaryService = dashboardSummaryService;
        this.gson = gson;
    }

    public AiDailyInsightsResponse generate(AiDailyInsightsRequest request, Principal principal) {
        long startedAt = System.nanoTime();
        permissionService.validateAiEnabled();

        AiSecurityContext context = securityContextResolver.resolve(principal);
        Long branchId = request.branchId();
        String date = request.date() == null || request.date().isBlank()
                ? LocalDate.now().toString()
                : request.date();
        LocalDate requestedDate = LocalDate.parse(date);
        LocalDate weekStartDate = weekStart(requestedDate);
        LocalDate nextWeekStartDate = weekStartDate.plusWeeks(1);
        String weekStart = weekStartDate.toString();
        String locale = request.locale() == null || request.locale().isBlank() ? "en" : request.locale();
        String cacheKey = insightCacheService.weeklyCacheKey(context.companyId(), branchId, weekStart, locale);

        permissionService.validateBranchRequired(AiMode.BUSINESS, branchId);
        permissionService.validateBranchAccess(context, branchId);
        permissionService.validateModeAccess(context, AiMode.BUSINESS);
        rateLimitService.validateDailyUserRequestLimit(context);
        costTrackingService.validateCompanyMonthlyTokenLimit(context);

        if (!request.shouldForceRefresh()) {
            return insightCacheService.getByKey(context, branchId, cacheKey, "WEEKLY_INSIGHTS")
                    .map(this::parseCachedResponse)
                    .map(cached -> new AiDailyInsightsResponse(
                            cached.insights(),
                            cached.source(),
                            cached.generatedAt(),
                            cached.date(),
                            cached.model(),
                            true
                    ))
                    .orElseGet(() -> generateFresh(request, context, branchId, weekStart, nextWeekStartDate, locale, cacheKey, startedAt));
        }

        return generateFresh(request, context, branchId, weekStart, nextWeekStartDate, locale, cacheKey, startedAt);
    }

    private AiDailyInsightsResponse generateFresh(AiDailyInsightsRequest request,
                                                  AiSecurityContext context,
                                                  Long branchId,
                                                  String weekStart,
                                                  LocalDate nextWeekStartDate,
                                                  String locale,
                                                  String cacheKey,
                                                  long startedAt) {
        UUID runId = UUID.randomUUID();
        List<SqlEvidence> evidence = new ArrayList<>();
        DashboardEvidence dashboardEvidence = collectDashboardSummaryEvidence(context, branchId, weekStart).orElse(null);
        if (dashboardEvidence != null) {
            evidence.add(dashboardEvidence.evidence());
        } else {
            evidence.addAll(collectSqlEvidence(context, branchId, weekStart, nextWeekStartDate, locale, runId));
        }
        if (evidence.stream().noneMatch(item -> item.success && item.answer != null && !item.answer.isBlank())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_WEEKLY_INSIGHTS_UNAVAILABLE", "AI could not analyze the database right now");
        }

        AiModelResponse modelResponse = null;
        List<AiDailyInsightDto> insights = List.of();
        try {
            modelResponse = modelClient.generate(new AiModelRequest(
                    buildSynthesisSystemPrompt(),
                    buildSynthesisUserPrompt(weekStart, nextWeekStartDate.toString(), locale, request.resolvedMaxInsights(), evidence),
                    "WEEKLY_INSIGHTS",
                    ""
            ));
            insights = parseInsights(modelResponse.answer(), request.resolvedMaxInsights());
        } catch (RuntimeException exception) {
            log.warn("AI weekly insight synthesis failed companyId={} branchId={} weekStart={} reason={}",
                    context.companyId(), branchId, weekStart, exception.getMessage());
        }

        if (insights.isEmpty()) {
            insights = dashboardEvidence != null
                    ? buildInsightsFromDashboardSummary(dashboardEvidence.summary(), request.resolvedMaxInsights())
                    : buildInsightsFromEvidence(evidence, request.resolvedMaxInsights());
        }

        AiDailyInsightsResponse response = new AiDailyInsightsResponse(
                insights,
                modelResponse == null ? "backend-summary" : "ai",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                weekStart,
                modelResponse == null ? "backend-summary" : modelResponse.modelName(),
                false
        );
        insightCacheService.putUntil(
                context,
                branchId,
                cacheKey,
                "weekly insights " + weekStart,
                gson.toJson(response),
                "{\"mode\":\"WEEKLY_INSIGHTS\",\"source\":\"" + (modelResponse == null ? "backend-summary" : "ai") + "\"}",
                nextWeekStartDate.atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        log.info("AI weekly insights generated companyId={} branchId={} weekStart={} expiresAt={} insightCount={} durationMs={}",
                context.companyId(), branchId, weekStart, nextWeekStartDate, insights.size(), elapsedMs(startedAt));
        return response;
    }

    private List<SqlEvidence> collectSqlEvidence(AiSecurityContext context,
                                                 Long branchId,
                                                 String weekStart,
                                                 LocalDate nextWeekStartDate,
                                                 String locale,
                                                 UUID runId) {
        String languageInstruction = locale.toLowerCase(Locale.ROOT).startsWith("ar")
                ? "Answer in Arabic."
                : "Answer in English.";
        String period = "the business week from " + weekStart + " through " + nextWeekStartDate.minusDays(1);
        List<String> questions = List.of(
                "For " + period + ", analyze sales totals, order count, income/profit, discounts, refunds or bounced-back sales, and compare with recent available data. " + languageInstruction,
                "For " + period + ", find the top selling products and categories by quantity and revenue, and identify what should be restocked or promoted. " + languageInstruction,
                "For the selected branch this week, identify low stock, out of stock, dead stock, damaged products, or inventory availability risks. " + languageInstruction,
                "For " + period + ", analyze cashier, employee, shift, payment, and cash movement patterns to identify staffing or cash-control issues. " + languageInstruction,
                "For " + period + ", analyze customers, suppliers, expenses, receivables, payables, and unposted finance work that needs manager attention. " + languageInstruction
        );

        List<SqlEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            String question = questions.get(index);
            try {
                AiSqlAgentService.AiSqlAnswer answer = sqlAgentService.answer(
                        context,
                        runId,
                        branchId,
                        question,
                        "This is the once-per-week dashboard insight generation job. Use only real DB rows."
                );
                evidence.add(new SqlEvidence("analysis_" + (index + 1), question, answer.answer(), answer.rowCount(), true, null));
            } catch (RuntimeException exception) {
                log.warn("AI weekly insight SQL evidence failed companyId={} branchId={} weekStart={} questionIndex={} reason={}",
                        context.companyId(), branchId, weekStart, index, exception.getMessage());
                evidence.add(new SqlEvidence("analysis_" + (index + 1), question, "", 0, false, exception.getMessage()));
            }
        }
        return evidence;
    }

    private java.util.Optional<DashboardEvidence> collectDashboardSummaryEvidence(AiSecurityContext context,
                                                                                  Long branchId,
                                                                                  String weekStart) {
        try {
            DashboardSummaryRequest request = new DashboardSummaryRequest();
            request.setBranchId(Math.toIntExact(branchId));
            request.setDate(weekStart);
            request.setPeriod("WEEK");
            DashboardSummaryResponse summary = dashboardSummaryService.getBranchSummary(Math.toIntExact(context.companyId()), request);
            JsonObject weeklyMetrics = new JsonObject();
            weeklyMetrics.addProperty("salesRevenue", weeklyRevenue(summary));
            weeklyMetrics.addProperty("grossProfit", weeklyGrossProfit(summary));
            weeklyMetrics.addProperty("netProfit", weeklyNetProfit(summary));
            weeklyMetrics.addProperty("marginPct", weeklyMargin(summary));
            weeklyMetrics.addProperty("periodStart", weekStart);
            JsonObject evidencePayload = new JsonObject();
            evidencePayload.add("weeklyMetrics", weeklyMetrics);
            evidencePayload.add("summary", gson.toJsonTree(summary));
            evidencePayload.addProperty(
                    "instruction",
                    "Use weeklyMetrics for weekly sales/profit. summary.kpis is daily KPI data for the selected date and must not be treated as weekly sales."
            );
            SqlEvidence evidence = new SqlEvidence(
                    "dashboard_summary",
                    "Backend dashboard summary for this week generated from real database providers.",
                    gson.toJson(evidencePayload),
                    1,
                    true,
                    null
            );
            return java.util.Optional.of(new DashboardEvidence(evidence, summary));
        } catch (RuntimeException exception) {
            log.warn("AI weekly dashboard summary evidence failed companyId={} branchId={} weekStart={} reason={}",
                    context.companyId(), branchId, weekStart, exception.getMessage());
            return java.util.Optional.empty();
        }
    }

    private String buildSynthesisSystemPrompt() {
        return """
                You are ValueInSoft's weekly business insight engine.
                You receive evidence produced from safe read-only database analysis and backend dashboard DB providers.
                Return strict JSON only. No Markdown and no commentary outside JSON.
                Do not invent numbers. Use only evidence you received.
                Prefer specific, actionable recommendations a retail branch manager can execute this week.
                """;
    }

    private String buildSynthesisUserPrompt(String weekStart, String nextWeekStart, String locale, int maxInsights, List<SqlEvidence> evidence) {
        String language = locale.toLowerCase(Locale.ROOT).startsWith("ar") ? "Arabic" : "English";
        return """
                Week start: %s
                Cache valid until next week: %s
                Language: %s
                Max insights: %d

                Return JSON with this exact shape:
                {
                  "insights": [
                    {
                      "id": "short_stable_id",
                      "type": "success|info|warning|critical",
                      "title": "short title",
                      "text": "specific insight and recommendation based on the evidence",
                      "action": "short button label",
                      "target": "viewInventory|FinanceReports|expensesView|PointSale",
                      "params": "optional query string",
                      "confidence": 0.0
                    }
                  ]
                }

                Evidence from DB analysis:
                %s
                """.formatted(weekStart, nextWeekStart, language, maxInsights, gson.toJson(evidence));
    }

    private List<AiDailyInsightDto> parseInsights(String rawAnswer, int maxInsights) {
        try {
            String json = stripCodeFence(rawAnswer);
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = object.has("insights") && object.get("insights").isJsonArray()
                    ? object.getAsJsonArray("insights")
                    : new JsonArray();
            List<AiDailyInsightDto> result = new ArrayList<>();
            for (JsonElement element : array) {
                if (result.size() >= maxInsights || !element.isJsonObject()) {
                    break;
                }
                JsonObject item = element.getAsJsonObject();
                String title = stringValue(item, "title");
                String text = stringValue(item, "text");
                if (title.isBlank() || text.isBlank()) {
                    continue;
                }
                result.add(new AiDailyInsightDto(
                        stringValue(item, "id").isBlank() ? "daily_insight_" + (result.size() + 1) : stringValue(item, "id"),
                        normalizeType(stringValue(item, "type")),
                        title,
                        text,
                        stringValue(item, "action"),
                        normalizeTarget(stringValue(item, "target")),
                        stringValue(item, "params"),
                        doubleValue(item, "confidence"),
                        "ai"
                ));
            }
            return result;
        } catch (RuntimeException exception) {
            log.warn("AI weekly insights JSON parse failed. answerLength={} reason={}",
                    rawAnswer == null ? 0 : rawAnswer.length(), exception.getMessage());
            return List.of();
        }
    }

    private List<AiDailyInsightDto> buildInsightsFromEvidence(List<SqlEvidence> evidence, int maxInsights) {
        List<AiDailyInsightDto> insights = evidence.stream()
                .filter(item -> item.success && item.answer != null && !item.answer.isBlank())
                .filter(item -> !looksLikeJson(item.answer))
                .limit(maxInsights)
                .map(item -> new AiDailyInsightDto(
                        item.id,
                        "info",
                        "Weekly DB analysis",
                        item.answer,
                        "Open reports",
                        "FinanceReports",
                        "",
                        null,
                        "ai"
                ))
                .toList();
        if (!insights.isEmpty()) {
            return insights;
        }
        return List.of(new AiDailyInsightDto(
                "weekly_review",
                "info",
                "Weekly review ready",
                "The database analysis completed, but no concise AI summary was available. Review the dashboard reports for this week's sales, inventory, and finance priorities.",
                "Open reports",
                "FinanceReports",
                "",
                null,
                "backend-summary"
        ));
    }

    private List<AiDailyInsightDto> buildInsightsFromDashboardSummary(DashboardSummaryResponse summary, int maxInsights) {
        List<AiDailyInsightDto> insights = new ArrayList<>();
        DashboardSummaryResponse.DashboardProfitSummary.ProfitData weekProfit =
                summary.getProfitSummary() == null ? null : summary.getProfitSummary().getWeek();
        DashboardSummaryResponse.DashboardInventoryHealth inventory = summary.getInventoryHealth();
        double weeklySales = weeklyRevenue(summary);

        if (weeklySales > 0) {
            insights.add(new AiDailyInsightDto(
                    "weekly_sales_focus",
                    "success",
                    "Weekly sales focus",
                    "Weekly sales are at " + formatNumber(weeklySales) + ". Review top items and keep fast movers available this week.",
                    "Open reports",
                    "FinanceReports",
                    "",
                    null,
                    "backend-summary"
            ));
        }

        if (weekProfit != null) {
            String type = weekProfit.getNetProfit() >= 0 ? "success" : "warning";
            insights.add(new AiDailyInsightDto(
                    "weekly_profit_control",
                    type,
                    "Profit control",
                    "Weekly net profit is " + formatNumber(weekProfit.getNetProfit()) + " with margin " + formatNumber(weekProfit.getMargin()) + "%. Watch expenses and discounting before the week closes.",
                    "Open reports",
                    "FinanceReports",
                    "",
                    null,
                    "backend-summary"
            ));
        }

        if (inventory != null) {
            double availability = safeNumber(inventory.getStockAvailabilityPct());
            double deadStock = safeNumber(inventory.getDeadStockPct());
            if (availability > 0 && availability < 90) {
                insights.add(new AiDailyInsightDto(
                        "weekly_stock_availability",
                        "warning",
                        "Stock availability risk",
                        "Stock availability is " + formatNumber(availability) + "%. Prioritize reordering products close to zero balance before peak selling days.",
                        "View inventory",
                        "viewInventory",
                        "",
                        null,
                        "backend-summary"
                ));
            }
            if (deadStock > 0) {
                insights.add(new AiDailyInsightDto(
                        "weekly_dead_stock",
                        deadStock >= 20 ? "warning" : "info",
                        "Dead stock pressure",
                        "Dead stock is " + formatNumber(deadStock) + "%. Bundle or discount slow moving stock this week before adding similar inventory.",
                        "View inventory",
                        "viewInventory",
                        "",
                        null,
                        "backend-summary"
                ));
            }
        }

        List<DashboardSummaryResponse.DashboardAlert> alerts = summary.getAlerts() == null ? List.of() : summary.getAlerts();
        for (DashboardSummaryResponse.DashboardAlert alert : alerts) {
            if (insights.size() >= maxInsights) {
                break;
            }
            insights.add(new AiDailyInsightDto(
                    alert.getId() == null ? "weekly_alert_" + insights.size() : alert.getId(),
                    normalizeType(alert.getType() == null ? alert.getSeverity() : alert.getType()),
                    alert.getTitle() == null ? "Needs attention" : alert.getTitle(),
                    alert.getMessage() == null ? "Review this item during the weekly operation check." : alert.getMessage(),
                    alert.getActionLabel() == null ? "Review" : alert.getActionLabel(),
                    normalizeTarget(alert.getTarget()),
                    alert.getParams() == null ? "" : alert.getParams(),
                    null,
                    "backend-summary"
            ));
        }

        if (insights.isEmpty()) {
            insights.add(new AiDailyInsightDto(
                    "weekly_review",
                    "info",
                    "Weekly review ready",
                    "The backend dashboard summary is available. Review sales, inventory, and finance sections to choose this week's operating priorities.",
                    "Open reports",
                    "FinanceReports",
                    "",
                    null,
                    "backend-summary"
            ));
        }

        return insights.stream().limit(maxInsights).toList();
    }

    private AiDailyInsightsResponse parseCachedResponse(String cachedJson) {
        try {
            return gson.fromJson(cachedJson, AiDailyInsightsResponse.class);
        } catch (RuntimeException exception) {
            log.warn("Failed to parse cached AI weekly insights response.", exception);
            return new AiDailyInsightsResponse(List.of(), "backend-summary", OffsetDateTime.now(ZoneOffset.UTC).toString(), "", "", true);
        }
    }

    private String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "");
            text = text.replaceFirst("(?s)\\s*```$", "");
        }
        return text.trim();
    }

    private boolean looksLikeJson(String value) {
        String text = value == null ? "" : value.trim();
        return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"));
    }

    private String stringValue(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString().trim();
    }

    private Double doubleValue(JsonObject object, String key) {
        try {
            if (!object.has(key) || object.get(key).isJsonNull()) {
                return null;
            }
            return object.get(key).getAsDouble();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT).trim();
        if (List.of("success", "info", "warning", "critical").contains(normalized)) {
            return normalized;
        }
        return "info";
    }

    private String normalizeTarget(String target) {
        String normalized = target == null ? "" : target.trim();
        if ("SalesReports".equals(normalized)) {
            return "FinanceReports";
        }
        if (List.of("viewInventory", "FinanceReports", "expensesView", "PointSale").contains(normalized)) {
            return normalized;
        }
        return "FinanceReports";
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private double safeNumber(Number value) {
        return value == null ? 0 : value.doubleValue();
    }

    private String formatNumber(Number value) {
        return String.format(Locale.US, "%,.2f", safeNumber(value));
    }

    private DashboardSummaryResponse.DashboardProfitSummary.ProfitData weeklyProfit(DashboardSummaryResponse summary) {
        if (summary == null || summary.getProfitSummary() == null) {
            return null;
        }
        return summary.getProfitSummary().getWeek();
    }

    private double weeklyRevenue(DashboardSummaryResponse summary) {
        DashboardSummaryResponse.DashboardProfitSummary.ProfitData week = weeklyProfit(summary);
        return week == null ? 0 : week.getRevenue();
    }

    private double weeklyGrossProfit(DashboardSummaryResponse summary) {
        DashboardSummaryResponse.DashboardProfitSummary.ProfitData week = weeklyProfit(summary);
        return week == null ? 0 : week.getGrossProfit();
    }

    private double weeklyNetProfit(DashboardSummaryResponse summary) {
        DashboardSummaryResponse.DashboardProfitSummary.ProfitData week = weeklyProfit(summary);
        return week == null ? 0 : week.getNetProfit();
    }

    private double weeklyMargin(DashboardSummaryResponse summary) {
        DashboardSummaryResponse.DashboardProfitSummary.ProfitData week = weeklyProfit(summary);
        return week == null ? 0 : week.getMargin();
    }

    private LocalDate weekStart(LocalDate date) {
        int daysSinceSaturday = Math.floorMod(date.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue(), 7);
        return date.minusDays(daysSinceSaturday);
    }

    private record SqlEvidence(
            String id,
            String question,
            String answer,
            int rowCount,
            boolean success,
            String error
    ) {
    }

    private record DashboardEvidence(
            SqlEvidence evidence,
            DashboardSummaryResponse summary
    ) {
    }
}
