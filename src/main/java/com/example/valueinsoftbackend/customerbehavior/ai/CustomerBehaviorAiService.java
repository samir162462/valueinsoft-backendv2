package com.example.valueinsoftbackend.customerbehavior.ai;

import com.example.valueinsoftbackend.ai.audit.AiUsageLogService;
import com.example.valueinsoftbackend.ai.cache.AiInsightCacheService;
import com.example.valueinsoftbackend.ai.service.AiCostTrackingService;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.example.valueinsoftbackend.ai.service.AiRateLimitService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.customerbehavior.config.CustomerBehaviorMetricRules;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorAiRequest;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorFilter;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorInsight;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorOverview;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorPage;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorRow;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerPreferenceSummary;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegment;
import com.example.valueinsoftbackend.customerbehavior.security.CustomerBehaviorRequestContext;
import com.example.valueinsoftbackend.customerbehavior.security.CustomerBehaviorSecurityService;
import com.example.valueinsoftbackend.customerbehavior.service.CustomerBehaviorAuditService;
import com.example.valueinsoftbackend.customerbehavior.service.CustomerBehaviorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CustomerBehaviorAiService {

    private static final String CACHE_MODE = "CUSTOMER_BEHAVIOR";
    private static final int AT_RISK_LIMIT = 10;

    private final CustomerBehaviorService behaviorService;
    private final CustomerBehaviorSecurityService securityService;
    private final CustomerBehaviorAuditService auditService;
    private final AiPermissionService aiPermissionService;
    private final AiRateLimitService rateLimitService;
    private final AiCostTrackingService costTrackingService;
    private final AiInsightCacheService cacheService;
    private final AiModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final AiUsageLogService usageLogService;

    public CustomerBehaviorAiService(CustomerBehaviorService behaviorService,
                                     CustomerBehaviorSecurityService securityService,
                                     CustomerBehaviorAuditService auditService,
                                     AiPermissionService aiPermissionService,
                                     AiRateLimitService rateLimitService,
                                     AiCostTrackingService costTrackingService,
                                     AiInsightCacheService cacheService,
                                     AiModelClient modelClient,
                                     ObjectMapper objectMapper,
                                     AiUsageLogService usageLogService) {
        this.behaviorService = behaviorService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.aiPermissionService = aiPermissionService;
        this.rateLimitService = rateLimitService;
        this.costTrackingService = costTrackingService;
        this.cacheService = cacheService;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.usageLogService = usageLogService;
    }

    public CustomerBehaviorInsight generate(CustomerBehaviorAiRequest request, Principal principal) {
        long startedAt = System.nanoTime();
        aiPermissionService.validateAiEnabled();
        CustomerBehaviorFilter filter = request == null ? null : request.filter();
        CustomerBehaviorRequestContext requestContext = securityService.authorizeAi(principal, filter);
        AiSecurityContext context = requestContext.aiContext();
        rateLimitService.validateDailyUserRequestLimit(context);
        costTrackingService.validateCompanyMonthlyTokenLimit(context);

        String locale = request == null || request.locale() == null || request.locale().isBlank() ? "en" : request.locale().trim();
        String cacheKey = cacheKey(requestContext, filter, locale);
        Long cacheBranchId = requestContext.branchIds().size() == 1 ? requestContext.branchIds().get(0).longValue() : null;
        if (request == null || !request.forceRefresh()) {
            return cacheService.getByKey(context, cacheBranchId, cacheKey, CACHE_MODE)
                    .map(this::parseCached)
                    .map(this::markCached)
                    .orElseGet(() -> generateFresh(context, requestContext, filter, locale, cacheKey, cacheBranchId, startedAt));
        }
        return generateFresh(context, requestContext, filter, locale, cacheKey, cacheBranchId, startedAt);
    }

    private CustomerBehaviorInsight generateFresh(AiSecurityContext context,
                                                  CustomerBehaviorRequestContext requestContext,
                                                  CustomerBehaviorFilter filter,
                                                  String locale,
                                                  String cacheKey,
                                                  Long cacheBranchId,
                                                  long startedAt) {
        CustomerBehaviorOverview overview = behaviorService.getOverview(context, filter);
        CustomerPreferenceSummary preferences = behaviorService.getPreferences(context, filter, 10);
        CustomerBehaviorPage<CustomerBehaviorRow> atRisk = behaviorService.searchCustomers(
                context,
                atRiskFilter(filter)
        );

        Map<String, Object> evidence = Map.of(
                "productName", "Customer Purchase Behavior Analytics",
                "metricsVersion", CustomerBehaviorMetricRules.METRICS_VERSION,
                "overview", overview,
                "preferences", preferences,
                "atRiskCustomers", atRisk.items()
        );

        AiModelResponse modelResponse = null;
        CustomerBehaviorInsight insight;
        boolean fallbackUsed = false;
        try {
            modelResponse = modelClient.generate(new AiModelRequest(
                    systemPrompt(),
                    userPrompt(locale, evidence),
                    CACHE_MODE,
                    ""
            ));
            insight = parseModelResponse(modelResponse, overview, preferences, locale);
            fallbackUsed = modelResponse.fallback();
        } catch (RuntimeException exception) {
            log.warn("Customer behavior AI generation failed companyId={} branches={} reason={}",
                    context.companyId(), requestContext.branchIds(), exception.getMessage());
            insight = fallbackInsight(overview, preferences, true, "backend-summary", locale);
            fallbackUsed = true;
        }

        if (insight == null) {
            insight = fallbackInsight(overview, preferences, true, "backend-summary", locale);
            fallbackUsed = true;
        }
        if (fallbackUsed && !insight.fallbackUsed()) {
            insight = withFallback(insight, true);
        }

        // Metered billing: consume the token usage recorded by the provider call above.
        usageLogService.logChatUsage(context.companyId(), context.userId(), null, elapsedMs(startedAt));

        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(6);
        cacheService.putUntil(
                context,
                cacheBranchId,
                cacheKey,
                "customer behavior insights " + overview.fromDate() + " " + overview.toDate(),
                toJson(insight),
                metadataJson(context, requestContext, overview, modelResponse, fallbackUsed),
                expiresAt
        );
        auditService.log(context.companyId(), cacheBranchId, context.userId(), "AI_INSIGHTS_GENERATED",
                Map.of("branchIds", requestContext.branchIds(), "fromDate", overview.fromDate(), "toDate", overview.toDate(),
                        "fallbackUsed", fallbackUsed),
                true,
                elapsedMs(startedAt));
        log.info("Customer behavior AI insights generated companyId={} branches={} fallbackUsed={} durationMs={}",
                context.companyId(), requestContext.branchIds(), fallbackUsed, elapsedMs(startedAt));
        return insight;
    }

    private CustomerBehaviorFilter atRiskFilter(CustomerBehaviorFilter filter) {
        return new CustomerBehaviorFilter(
                filter == null ? null : filter.branchIds(),
                filter == null ? null : filter.fromDate(),
                filter == null ? null : filter.toDate(),
                CustomerSegment.AT_RISK,
                null,
                null,
                0,
                AT_RISK_LIMIT,
                "daysInactive",
                "desc"
        );
    }

    private CustomerBehaviorInsight parseModelResponse(AiModelResponse response,
                                                       CustomerBehaviorOverview overview,
                                                       CustomerPreferenceSummary preferences,
                                                       String locale) {
        if (response == null || response.answer() == null || response.answer().isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(response.answer()));
            if (!root.isObject()) {
                return null;
            }
            return new CustomerBehaviorInsight(
                    requiredText(root, "summary"),
                    stringList(root, "keyPatterns"),
                    stringList(root, "prioritySegments"),
                    stringList(root, "recommendedActions"),
                    root.path("confidence").isNumber() ? root.path("confidence").asDouble() : 0.0,
                    stringList(root, "warnings"),
                    stringList(root, "dataGaps"),
                    "ai",
                    false,
                    OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    response.modelName(),
                    response.fallback()
            );
        } catch (Exception exception) {
            log.warn("Customer behavior AI returned invalid JSON. Falling back to deterministic summary.");
            return fallbackInsight(overview, preferences, true, response.modelName(), locale);
        }
    }

    private CustomerBehaviorInsight fallbackInsight(CustomerBehaviorOverview overview,
                                                    CustomerPreferenceSummary preferences,
                                                    boolean fallbackUsed,
                                                    String modelName,
                                                    String locale) {
        boolean isAr = locale != null && locale.trim().toLowerCase().startsWith("ar");
        if (isAr) {
            return fallbackArabicInsight(overview, preferences, fallbackUsed, modelName);
        }
        List<String> keyPatterns = new ArrayList<>();
        if (isAr) {
            keyPatterns.add("العملاء المشترون: " + overview.purchasingCustomers());
            keyPatterns.add("معدل تكرار الشراء: " + overview.repeatPurchaseRate());
            keyPatterns.add("العملاء المعرضون للعزوف: " + overview.atRiskCustomerCount());
            if (preferences != null && preferences.topCategories() != null && !preferences.topCategories().isEmpty()) {
                keyPatterns.add("الفئة الأكثر تفضيلاً: " + preferences.topCategories().get(0).name());
            }
        } else {
            keyPatterns.add("Purchasing customers: " + overview.purchasingCustomers());
            keyPatterns.add("Repeat purchase rate: " + overview.repeatPurchaseRate());
            keyPatterns.add("At-risk customers: " + overview.atRiskCustomerCount());
            if (preferences != null && preferences.topCategories() != null && !preferences.topCategories().isEmpty()) {
                keyPatterns.add("Top preferred category: " + preferences.topCategories().get(0).name());
            }
        }

        List<String> actions = new ArrayList<>();
        if (overview.atRiskCustomerCount() > 0) {
            actions.add(isAr ? "قم بإعطاء الأولوية للعملاء المعرضين للعزوف عبر سير عمل مخصص لاستعادتهم." : "Prioritize at-risk customers with a targeted win-back workflow.");
        }
        actions.add(isAr ? "قم بترويج المنتجات من فئات تفضيلات العملاء الأقوى." : "Promote products from the strongest customer preference categories.");
        actions.add(isAr ? "راجع نسب الخصم والمرتجع قبل إطلاق العروض العامة." : "Review discount and return ratios before launching broad offers.");

        String summary = isAr ? "تم إنشاء تحليلات سلوك شراء العملاء من مقاييس شراء محددة وقواعد ثابتة." : "Customer Purchase Behavior Analytics was generated from deterministic purchase metrics.";
        List<String> dataGaps = isAr ? List.of("لا تتوفر مشاهدات للمنتجات، أو أحداث سلة التسوق، أو أحداث البحث، أو جلسات الويب، أو إحالات الحملات في المخطط الحالي.") : List.of("No product views, cart events, search events, web sessions, or campaign attribution are available in the current schema.");

        return new CustomerBehaviorInsight(
                summary,
                keyPatterns,
                List.of("AT_RISK", "VIP", "LOYAL"),
                actions,
                0.72,
                overview.dataQualityWarnings(),
                dataGaps,
                "backend-summary",
                false,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                modelName,
                fallbackUsed
        );
    }

    private CustomerBehaviorInsight fallbackArabicInsight(CustomerBehaviorOverview overview,
                                                          CustomerPreferenceSummary preferences,
                                                          boolean fallbackUsed,
                                                          String modelName) {
        List<String> keyPatterns = new ArrayList<>();
        keyPatterns.add("العملاء اللي اشتروا: " + overview.purchasingCustomers());
        keyPatterns.add("معدل تكرار الشراء: " + overview.repeatPurchaseRate());
        keyPatterns.add("عملاء محتاجين متابعة: " + overview.atRiskCustomerCount());
        if (preferences != null && preferences.topCategories() != null && !preferences.topCategories().isEmpty()) {
            keyPatterns.add("أكتر فئة العملاء بيفضلوها: " + preferences.topCategories().get(0).name());
        }

        List<String> actions = new ArrayList<>();
        if (overview.atRiskCustomerCount() > 0) {
            actions.add("ابدأ بحملة رجوع مخصصة للعملاء اللي بقالهم فترة ما اشتروش.");
        }
        actions.add("ركّز العروض على الفئات اللي العملاء بيشتروها أكتر بدل عروض عامة.");
        actions.add("راجع الخصومات والمرتجعات قبل ما تطلع عروض كبيرة عشان تحافظ على الهامش.");

        return new CustomerBehaviorInsight(
                "تم توليد تحليل سلوك العملاء من بيانات الشراء الفعلية في الفترة المحددة، مع توضيح العملاء النشطين والمتكررين والعملاء اللي محتاجين متابعة.",
                keyPatterns,
                List.of("AT_RISK", "VIP", "LOYAL"),
                actions,
                0.72,
                overview.dataQualityWarnings(),
                List.of("النظام الحالي مش بيسجل مشاهدات المنتجات أو السلة أو البحث أو جلسات الويب أو مصدر الحملات."),
                "backend-summary",
                false,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                modelName,
                fallbackUsed
        );
    }

    private String systemPrompt() {
        return """
                You summarize Customer Purchase Behavior Analytics for a multi-tenant SaaS.
                Use only the trusted DTO values supplied by the backend.
                Never calculate financial metrics yourself and never invent missing omnichannel behavior data.
                Do not ask for or expose raw phone numbers, addresses, card details, or customer notes.
                Return strict JSON only with keys:
                summary, keyPatterns, prioritySegments, recommendedActions, confidence, warnings, dataGaps.
                Respond in the language specified by the requested Locale. If Locale is 'ar' or starts with 'ar', return all user-facing JSON text in clear Egyptian Arabic dialect, not English and not Modern Standard Arabic. Otherwise, return English.
                The prioritySegments list contains segment keys which must always remain in English (e.g. "VIP", "LOYAL", "AT_RISK"). All other text field values must be translated.
                """;
    }

    private String userPrompt(String locale, Map<String, Object> evidence) {
        return """
                Locale: %s
                If Locale starts with ar: write the summary, keyPatterns, recommendedActions, warnings, and dataGaps in Egyptian Arabic. Keep only prioritySegments keys in English.
                Product scope: Customer Purchase Behavior Analytics only. This system does not collect product views, search events, cart events, abandoned carts, wishlists, campaign attribution, or web sessions.
                Trusted analytics DTO JSON:
                %s
                """.formatted(locale, toJson(evidence));
    }

    private CustomerBehaviorInsight parseCached(String cachedJson) {
        try {
            return objectMapper.readValue(cachedJson, CustomerBehaviorInsight.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private CustomerBehaviorInsight markCached(CustomerBehaviorInsight insight) {
        if (insight == null) {
            return null;
        }
        return new CustomerBehaviorInsight(
                insight.summary(),
                insight.keyPatterns(),
                insight.prioritySegments(),
                insight.recommendedActions(),
                insight.confidence(),
                insight.warnings(),
                insight.dataGaps(),
                insight.source(),
                true,
                insight.generatedAt(),
                insight.modelName(),
                insight.fallbackUsed()
        );
    }

    private CustomerBehaviorInsight withFallback(CustomerBehaviorInsight insight, boolean fallbackUsed) {
        return new CustomerBehaviorInsight(
                insight.summary(),
                insight.keyPatterns(),
                insight.prioritySegments(),
                insight.recommendedActions(),
                insight.confidence(),
                insight.warnings(),
                insight.dataGaps(),
                insight.source(),
                insight.cached(),
                insight.generatedAt(),
                insight.modelName(),
                fallbackUsed
        );
    }

    private String metadataJson(AiSecurityContext context,
                                CustomerBehaviorRequestContext requestContext,
                                CustomerBehaviorOverview overview,
                                AiModelResponse modelResponse,
                                boolean fallbackUsed) {
        return toJson(Map.of(
                "companyId", context.companyId(),
                "branchFilterHash", sha(String.valueOf(requestContext.branchIds())),
                "dateRange", overview.fromDate() + ":" + overview.toDate(),
                "metricsVersion", CustomerBehaviorMetricRules.METRICS_VERSION,
                "promptVersion", CustomerBehaviorMetricRules.PROMPT_VERSION,
                "modelName", modelResponse == null ? "backend-summary" : modelResponse.modelName(),
                "generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "fallbackUsed", fallbackUsed
        ));
    }

    private String cacheKey(CustomerBehaviorRequestContext requestContext, CustomerBehaviorFilter filter, String locale) {
        String raw = requestContext.companyId()
                + "|" + requestContext.branchIds()
                + "|" + (filter == null ? "" : filter.fromDate())
                + "|" + (filter == null ? "" : filter.toDate())
                + "|" + CustomerBehaviorMetricRules.METRICS_VERSION
                + "|" + CustomerBehaviorMetricRules.PROMPT_VERSION
                + "|" + locale;
        return sha(raw);
    }

    private String sha(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (RuntimeException exception) {
            return Integer.toHexString(raw.hashCode());
        } catch (Exception exception) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            return "{}";
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : "";
    }

    private List<String> stringList(JsonNode root, String field) {
        JsonNode node = root.path(field);
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

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
