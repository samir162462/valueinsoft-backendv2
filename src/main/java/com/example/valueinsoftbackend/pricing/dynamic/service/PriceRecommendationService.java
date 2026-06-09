package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationItemsPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationRunRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationRunResponse;
import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceRecommendationStatus;
import com.example.valueinsoftbackend.pricing.dynamic.model.PricingRecommendation;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceRecommendationRunRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PricingMetricsRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationItemResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class PriceRecommendationService {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int DEFAULT_MAX_PRODUCTS = 100;
    private static final int MAX_WINDOW_DAYS = 180;

    private final PricingMetricsRepository metricsRepository;
    private final DynamicPricingPolicyRepository policyRepository;
    private final PriceRecommendationRunRepository runRepository;
    private final PriceRecommendationCalculator calculator;
    private final DynamicPricingSecurityService securityService;
    private final PricingAuditService auditService;

    public PriceRecommendationService(PricingMetricsRepository metricsRepository,
                                      DynamicPricingPolicyRepository policyRepository,
                                      PriceRecommendationRunRepository runRepository,
                                      PriceRecommendationCalculator calculator,
                                      DynamicPricingSecurityService securityService,
                                      PricingAuditService auditService) {
        this.metricsRepository = metricsRepository;
        this.policyRepository = policyRepository;
        this.runRepository = runRepository;
        this.calculator = calculator;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    public PriceRecommendationRunResponse createRun(String actorName, PriceRecommendationRunRequest request) {
        securityService.requireRecommendationRun(actorName, request.companyId(), request.branchId());

        int windowDays = request.metricsWindowDays() == null ? DEFAULT_WINDOW_DAYS : request.metricsWindowDays();
        int maxProducts = request.maxProducts() == null ? DEFAULT_MAX_PRODUCTS : request.maxProducts();
        LocalDate toDate = request.toDate() == null ? LocalDate.now() : request.toDate();
        LocalDate fromDate = toDate.minusDays(windowDays - 1L);
        validateWindow(windowDays);

        long runId = 0L;
        try {
            runId = runRepository.createRun(
                    request.companyId(),
                    request.branchId(),
                    windowDays,
                    actorName,
                    scopeJson(request, fromDate, toDate, maxProducts),
                    "{\"mode\":\"effective_policy_per_product\",\"calculator\":\"deterministic_v1\"}"
            );
            auditService.log(request.companyId(), request.branchId(), "RECOMMENDATION_RUN_CREATED",
                    "RUN", String.valueOf(runId), actorName,
                    "Dynamic pricing recommendation run created", "{\"runId\":" + runId + "}");

            PricingMetricsRepository.MetricsPage metricsPage = metricsRepository.findMetrics(new PricingMetricsRepository.MetricsQuery(
                    request.companyId(),
                    request.branchId(),
                    fromDate,
                    toDate,
                    request.query(),
                    request.productIds(),
                    request.category(),
                    request.major(),
                    request.businessLineKey(),
                    request.templateKey(),
                    request.supplierId(),
                    0,
                    maxProducts
            ));

            int recommended = 0;
            int warnings = 0;
            int skipped = 0;
            for (var metrics : metricsPage.items()) {
                DynamicPricingPolicy policy = policyRepository.findEffectivePolicy(request.companyId(), request.branchId(), metrics.productId())
                        .orElseGet(() -> policyRepository.systemDefaultPolicy(request.companyId(), request.branchId()));
                PricingRecommendation recommendation = calculator.calculate(metrics, policy);
                runRepository.insertItem(request.companyId(), request.branchId(), runId, recommendation);

                if (recommendation.status() == PriceRecommendationStatus.RECOMMENDED) {
                    recommended++;
                } else if (recommendation.status() == PriceRecommendationStatus.WARNING) {
                    warnings++;
                } else if (recommendation.status() == PriceRecommendationStatus.SKIPPED) {
                    skipped++;
                }
            }

            runRepository.completeRun(request.companyId(), runId, metricsPage.items().size(), recommended, warnings, skipped);
            auditService.log(request.companyId(), request.branchId(), "RECOMMENDATION_RUN_COMPLETED",
                    "RUN", String.valueOf(runId), actorName,
                    "Dynamic pricing recommendation run completed",
                    "{\"runId\":" + runId + ",\"totalProducts\":" + metricsPage.items().size() + "}");
            return runRepository.findRun(request.companyId(), runId);
        } catch (Exception exception) {
            if (runId > 0) {
                runRepository.failRun(request.companyId(), runId, truncate(exception.getMessage()));
                auditService.log(request.companyId(), request.branchId(), "RECOMMENDATION_RUN_FAILED",
                        "RUN", String.valueOf(runId), actorName,
                        "Dynamic pricing recommendation run failed",
                        "{\"runId\":" + runId + ",\"error\":\"" + jsonEscape(truncate(exception.getMessage())) + "\"}");
            }
            throw exception;
        }
    }

    public PriceRecommendationRunResponse getRun(String actorName, int companyId, int branchId, long runId) {
        securityService.requireView(actorName, companyId, branchId);
        PriceRecommendationRunResponse response = runRepository.findRun(companyId, runId);
        if (response.branchId() != branchId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRICING_RECOMMENDATION_RUN_NOT_FOUND",
                    "Recommendation run was not found for this branch");
        }
        return response;
    }

    public PriceRecommendationItemsPageResponse getItems(String actorName, int companyId, int branchId, long runId,
                                                         String status, int page, int size) {
        securityService.requireView(actorName, companyId, branchId);
        boolean includeCost = securityService.canReadCost(actorName, companyId, branchId);
        PriceRecommendationRunRepository.ItemsPage itemsPage = runRepository.findItems(
                companyId,
                branchId,
                runId,
                normalizeStatus(status),
                page,
                size,
                includeCost
        );
        return new PriceRecommendationItemsPageResponse(
                itemsPage.page(),
                itemsPage.size(),
                itemsPage.totalItems(),
                itemsPage.totalPages(),
                includeCost,
                itemsPage.items()
        );
    }

    public PriceRecommendationItemResponse updateItemPrice(String actorName, int companyId, int branchId, long itemId, BigDecimal suggestedRetailPrice) {
        securityService.requireRecommendationRun(actorName, companyId, branchId);

        PriceRecommendationItemResponse item = runRepository.findItem(companyId, branchId, itemId);
        BigDecimal oldRetail = item.oldRetailPrice();
        BigDecimal buying = item.buyingPrice();

        BigDecimal deltaAmount = suggestedRetailPrice.subtract(oldRetail).setScale(4, RoundingMode.HALF_UP);
        BigDecimal deltaPct = oldRetail.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : deltaAmount.divide(oldRetail, 4, RoundingMode.HALF_UP);

        BigDecimal suggestedMargin = (suggestedRetailPrice.compareTo(BigDecimal.ZERO) <= 0 || buying == null)
                ? null
                : suggestedRetailPrice.subtract(buying).divide(suggestedRetailPrice, 4, RoundingMode.HALF_UP);

        runRepository.updateSuggestedPrice(companyId, branchId, itemId, suggestedRetailPrice, deltaAmount, deltaPct, suggestedMargin);

        return runRepository.findItem(companyId, branchId, itemId);
    }

    public void bulkRoundPrices(String actorName, int companyId, int branchId, long runId, BigDecimal roundingFactor) {
        securityService.requireRecommendationRun(actorName, companyId, branchId);
        runRepository.bulkRoundSuggestedPrices(companyId, branchId, runId, roundingFactor);
    }


    private void validateWindow(int windowDays) {
        if (windowDays < 1 || windowDays > MAX_WINDOW_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_RECOMMENDATION_WINDOW_INVALID",
                    "Recommendation metrics window must be between 1 and " + MAX_WINDOW_DAYS + " days");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PriceRecommendationStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_RECOMMENDATION_STATUS_INVALID",
                    "Recommendation status filter is invalid");
        }
    }

    private String scopeJson(PriceRecommendationRunRequest request, LocalDate fromDate, LocalDate toDate, int maxProducts) {
        return String.format(Locale.ROOT, """
                {"companyId":%d,"branchId":%d,"fromDate":"%s","toDate":"%s","query":%s,"productIdsCount":%d,"category":%s,"major":%s,"businessLineKey":%s,"templateKey":%s,"supplierId":%s,"maxProducts":%d}
                """,
                request.companyId(),
                request.branchId(),
                fromDate,
                toDate,
                jsonString(request.query()),
                request.productIds() == null ? 0 : request.productIds().size(),
                jsonString(request.category()),
                jsonString(request.major()),
                jsonString(request.businessLineKey()),
                jsonString(request.templateKey()),
                request.supplierId() == null ? "null" : request.supplierId().toString(),
                maxProducts
        ).trim();
    }

    private static String jsonString(String value) {
        return value == null || value.isBlank() ? "null" : "\"" + jsonEscape(value.trim()) + "\"";
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String value) {
        if (value == null) {
            return "Unknown recommendation run failure";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
