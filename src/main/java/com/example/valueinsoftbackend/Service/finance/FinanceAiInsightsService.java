package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAiFocusAccount;
import com.example.valueinsoftbackend.Model.Finance.FinanceAiInsightsResponse;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAiInsightsRequest;
import com.example.valueinsoftbackend.ai.cache.AiInsightCacheService;
import com.example.valueinsoftbackend.ai.service.AiCostTrackingService;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.example.valueinsoftbackend.ai.service.AiRateLimitService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.ai.service.AiSecurityContextResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class FinanceAiInsightsService {

    private static final String CACHE_MODE = "FINANCE_AI_INSIGHTS";
    private static final int SNAPSHOT_JSON_LIMIT = 14_000;

    private final AiPermissionService aiPermissionService;
    private final AiRateLimitService rateLimitService;
    private final AiCostTrackingService costTrackingService;
    private final AiSecurityContextResolver securityContextResolver;
    private final AiInsightCacheService cacheService;
    private final AiModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final com.example.valueinsoftbackend.ai.audit.AiUsageLogService usageLogService;

    public FinanceAiInsightsService(AiPermissionService aiPermissionService,
                                    AiRateLimitService rateLimitService,
                                    AiCostTrackingService costTrackingService,
                                    AiSecurityContextResolver securityContextResolver,
                                    AiInsightCacheService cacheService,
                                    AiModelClient modelClient,
                                    ObjectMapper objectMapper,
                                    com.example.valueinsoftbackend.ai.audit.AiUsageLogService usageLogService) {
        this.aiPermissionService = aiPermissionService;
        this.rateLimitService = rateLimitService;
        this.costTrackingService = costTrackingService;
        this.securityContextResolver = securityContextResolver;
        this.cacheService = cacheService;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.usageLogService = usageLogService;
    }

    public FinanceAiInsightsResponse generate(FinanceAiInsightsRequest request, Principal principal) {
        aiPermissionService.validateAiEnabled();
        AiSecurityContext context = securityContextResolver.resolve(principal);
        validateScope(request, context);

        Long cacheBranchId = request.branchId() == null ? null : request.branchId().longValue();
        String snapshotJson = snapshotJson(request.reportSnapshot());
        String cacheKey = cacheKey(request, snapshotJson);
        FinanceAiInsightsResponse cached = cacheService.getByKey(context, cacheBranchId, cacheKey, CACHE_MODE)
                .map(this::parseCached)
                .map(this::markCached)
                .orElse(null);
        if (cached != null) {
            log.info("Finance AI insights served from 24h memory companyId={} branchId={} reportType={}",
                    context.companyId(), request.branchId(), request.reportType());
            return cached;
        }

        rateLimitService.validateDailyUserRequestLimit(context);
        costTrackingService.validateCompanyMonthlyTokenLimit(context);

        long startedAt = System.nanoTime();
        AiModelResponse modelResponse = modelClient.generate(new AiModelRequest(
                systemPrompt(),
                userPrompt(request, snapshotJson),
                "FINANCE",
                "",
                "",
                "deepseek"
        ));
        // Metered billing: consume the token usage recorded by the provider call above.
        usageLogService.logChatUsage(context.companyId(), context.userId(), null,
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L));

        FinanceAiInsightsResponse parsed = parseModelResponse(modelResponse);
        if (parsed != null) {
            cacheIfUseful(context, cacheBranchId, cacheKey, request, parsed);
            return parsed;
        }

        FinanceAiInsightsResponse fallback = fallbackResponse(modelResponse);
        cacheIfUseful(context, cacheBranchId, cacheKey, request, fallback);
        return fallback;
    }

    private void validateScope(FinanceAiInsightsRequest request, AiSecurityContext context) {
        if (request == null || request.companyId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_AI_REQUEST_INVALID", "Finance AI request is invalid");
        }
        if (request.companyId() != context.companyId()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FINANCE_AI_COMPANY_ACCESS_DENIED", "Company access denied");
        }
        if (request.branchId() != null) {
            securityContextResolver.validateBranchAccess(context, request.branchId().longValue());
        }
        if (request.reportSnapshot() == null || request.reportSnapshot().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_AI_SNAPSHOT_REQUIRED", "Finance report snapshot is required");
        }
    }

    private FinanceAiInsightsResponse parseModelResponse(AiModelResponse response) {
        if (response == null || response.answer() == null || response.answer().isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(response.answer()));
            if (!root.isObject()) {
                return null;
            }
            return new FinanceAiInsightsResponse(
                    text(root, "summary", safeSummary(response.answer())),
                    normalizeRisk(text(root, "riskLevel", "watch")),
                    stringList(root, "recommendedActions"),
                    focusAccounts(root.path("focusAccounts")),
                    response.fallback() || root.path("fallbackUsed").asBoolean(false),
                    blankToDefault(response.modelName(), text(root, "modelName", "deepseek-chat")),
                    response.fallback() ? "ai_provider_fallback" : text(root, "source", "deepseek"),
                    OffsetDateTime.now(ZoneOffset.UTC).toString()
            );
        } catch (Exception exception) {
            log.warn("Finance AI returned non-JSON response. Returning answer as summary.");
            return null;
        }
    }

    private FinanceAiInsightsResponse fallbackResponse(AiModelResponse response) {
        boolean providerFallback = response == null || response.fallback();
        return new FinanceAiInsightsResponse(
                response == null || response.answer() == null || response.answer().isBlank()
                        ? "Finance AI response is temporarily unavailable."
                        : safeSummary(response.answer()),
                "watch",
                providerFallback
                        ? List.of("Check the DeepSeek API key and backend AI logs, then refresh insights.")
                        : List.of("Review this AI summary against the posted finance report before action."),
                List.of(),
                providerFallback,
                response == null ? "deepseek-chat" : blankToDefault(response.modelName(), "deepseek-chat"),
                providerFallback ? "deepseek_unavailable" : "deepseek_text",
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    private FinanceAiInsightsResponse parseCached(String cachedJson) {
        try {
            return objectMapper.readValue(cachedJson, FinanceAiInsightsResponse.class);
        } catch (Exception exception) {
            log.debug("Finance AI cached insight could not be parsed. Ignoring cache entry.");
            return null;
        }
    }

    private FinanceAiInsightsResponse markCached(FinanceAiInsightsResponse insight) {
        if (insight == null) {
            return null;
        }
        return new FinanceAiInsightsResponse(
                insight.summary(),
                insight.riskLevel(),
                insight.recommendedActions(),
                insight.focusAccounts(),
                insight.fallbackUsed(),
                insight.modelName(),
                insight.source() == null || insight.source().isBlank() ? "deepseek_cached_24h" : insight.source() + "_cached_24h",
                insight.generatedAt()
        );
    }

    private void cacheIfUseful(AiSecurityContext context,
                               Long branchId,
                               String cacheKey,
                               FinanceAiInsightsRequest request,
                               FinanceAiInsightsResponse response) {
        if (response == null || "deepseek_unavailable".equalsIgnoreCase(response.source())) {
            return;
        }
        cacheService.putUntil(
                context,
                branchId,
                cacheKey,
                "finance ai insights " + blankToDefault(request.reportType(), "report") + " " + request.fiscalPeriodId(),
                toJson(response),
                metadataJson(request, response),
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(24)
        );
    }

    private String systemPrompt() {
        return """
                You are ValueInSoft Finance AI for accounting reports.
                Use only the trusted reportSnapshot supplied by the backend/frontend report API.
                Posted journals and finance report APIs are the source of truth.
                Do not invent accounts, journal lines, balances, dates, or tax/legal conclusions.
                Return strict JSON only with keys:
                summary, riskLevel, recommendedActions, focusAccounts, source.
                riskLevel must be one of: healthy, watch, warning, critical.
                focusAccounts must be an array of objects with optional accountId, accountCode, accountName, and reason.
                """;
    }

    private String userPrompt(FinanceAiInsightsRequest request, String snapshotJson) {
        String locale = request.locale() == null || request.locale().isBlank() ? "en" : request.locale().trim();
        String languageInstruction = locale.toLowerCase(Locale.ROOT).startsWith("ar")
                ? "Respond with Arabic user-facing text."
                : "Respond with English user-facing text.";
        return """
                Locale: %s
                %s
                Report type: %s
                Company id: %s
                Fiscal period id: %s
                Branch id: %s
                Currency: %s

                Analyze close readiness, accounting classification risk, unusual balances, major account drivers, and next accountant actions.
                Keep the summary short and operational.

                reportSnapshot JSON:
                %s
                """.formatted(
                locale,
                languageInstruction,
                blankToDefault(request.reportType(), "unknown"),
                request.companyId(),
                request.fiscalPeriodId(),
                request.branchId(),
                blankToDefault(request.currencyCode(), "EGP"),
                limitedJson(snapshotJson)
        );
    }

    private String snapshotJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot == null ? Map.of() : snapshot);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String limitedJson(String json) {
        String value = json == null || json.isBlank() ? "{}" : json;
        if (value.length() <= SNAPSHOT_JSON_LIMIT) {
            return value;
        }
        return value.substring(0, SNAPSHOT_JSON_LIMIT) + "...[truncated]";
    }

    private String cacheKey(FinanceAiInsightsRequest request, String snapshotJson) {
        String raw = request.companyId()
                + "|" + request.branchId()
                + "|" + request.fiscalPeriodId()
                + "|" + blankToDefault(request.reportType(), "unknown").toLowerCase(Locale.ROOT)
                + "|" + blankToDefault(request.currencyCode(), "EGP").toUpperCase(Locale.ROOT)
                + "|" + blankToDefault(request.locale(), "en").toLowerCase(Locale.ROOT)
                + "|" + sha(snapshotJson);
        return sha(raw);
    }

    private String metadataJson(FinanceAiInsightsRequest request, FinanceAiInsightsResponse response) {
        return toJson(Map.of(
                "mode", CACHE_MODE,
                "reportType", blankToDefault(request.reportType(), "unknown"),
                "fiscalPeriodId", String.valueOf(request.fiscalPeriodId()),
                "currencyCode", blankToDefault(request.currencyCode(), "EGP"),
                "source", blankToDefault(response.source(), "deepseek"),
                "modelName", blankToDefault(response.modelName(), "deepseek-chat"),
                "generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "ttlHours", 24
        ));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String sha(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            return Integer.toHexString(String.valueOf(raw).hashCode());
        }
    }

    private List<String> stringList(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private List<FinanceAiFocusAccount> focusAccounts(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<FinanceAiFocusAccount> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            values.add(new FinanceAiFocusAccount(
                    text(item, "accountId", ""),
                    text(item, "accountCode", ""),
                    text(item, "accountName", ""),
                    text(item, "reason", "")
            ));
        }
        return values;
    }

    private String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private String extractJsonObject(String value) {
        String candidate = stripCodeFence(value);
        int firstBrace = candidate.indexOf('{');
        int lastBrace = candidate.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return candidate.substring(firstBrace, lastBrace + 1);
        }
        return candidate;
    }

    private String text(JsonNode root, String fieldName, String defaultValue) {
        JsonNode node = root.path(fieldName);
        if (node.isTextual() && !node.asText().isBlank()) {
            return node.asText();
        }
        return defaultValue;
    }

    private String normalizeRisk(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("healthy") || normalized.equals("watch") || normalized.equals("warning") || normalized.equals("critical")) {
            return normalized;
        }
        return "watch";
    }

    private String safeSummary(String value) {
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 700) {
            return compact;
        }
        return compact.substring(0, 697) + "...";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
