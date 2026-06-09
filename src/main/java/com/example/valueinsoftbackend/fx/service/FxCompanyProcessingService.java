package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.fx.model.FxCompanyConfig;
import com.example.valueinsoftbackend.fx.model.FxCompanyProcessingResult;
import com.example.valueinsoftbackend.fx.model.FxCompanyProcessingSummary;
import com.example.valueinsoftbackend.fx.model.FxProductReplacementCost;
import com.example.valueinsoftbackend.fx.model.GlobalFxRateSnapshot;
import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceRecommendationStatus;
import com.example.valueinsoftbackend.pricing.dynamic.model.PricingMetricsSnapshot;
import com.example.valueinsoftbackend.pricing.dynamic.model.PricingRecommendation;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceRecommendationRunRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PricingMetricsRepository;
import com.example.valueinsoftbackend.pricing.dynamic.service.PriceRecommendationCalculator;
import com.example.valueinsoftbackend.fx.repository.FxCompanyProcessingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class FxCompanyProcessingService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    private final FxCompanyProcessingRepository repository;
    private final PricingMetricsRepository metricsRepository;
    private final DynamicPricingPolicyRepository policyRepository;
    private final PriceRecommendationRunRepository runRepository;
    private final PriceRecommendationCalculator calculator;
    private final FxDeepSeekProperties properties;
    private final TransactionTemplate companyTransactionTemplate;

    public FxCompanyProcessingService(FxCompanyProcessingRepository repository,
                                      PricingMetricsRepository metricsRepository,
                                      DynamicPricingPolicyRepository policyRepository,
                                      PriceRecommendationRunRepository runRepository,
                                      PriceRecommendationCalculator calculator,
                                      FxDeepSeekProperties properties,
                                      PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.metricsRepository = metricsRepository;
        this.policyRepository = policyRepository;
        this.runRepository = runRepository;
        this.calculator = calculator;
        this.properties = properties;
        this.companyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.companyTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public FxCompanyProcessingSummary processAllEligibleCompanies(GlobalFxRateSnapshot snapshot) {
        int totalActiveCompanies = repository.countActiveCompanies();
        List<FxCompanyConfig> eligibleCompanies = repository.findActiveFxEnabledCompanies();

        int succeeded = 0;
        int failed = 0;
        int skipped = 0;
        int totalEvaluatedProducts = 0;
        int totalRecommendationsGenerated = 0;

        int batchSize = Math.max(1, properties.getCompanyBatchSize());
        for (int index = 0; index < eligibleCompanies.size(); index += batchSize) {
            List<FxCompanyConfig> batch = eligibleCompanies.subList(index, Math.min(index + batchSize, eligibleCompanies.size()));
            for (FxCompanyConfig config : batch) {
                try {
                    FxCompanyProcessingResult result = companyTransactionTemplate.execute(status -> processCompany(snapshot, config));
                    if (result == null || "SKIPPED".equals(result.status())) {
                        skipped++;
                    } else {
                        succeeded++;
                        totalEvaluatedProducts += result.evaluatedProducts();
                        totalRecommendationsGenerated += result.recommendationsGenerated();
                    }
                } catch (Exception exception) {
                    failed++;
                    log.warn("FX company processing failed companyId={} snapshotId={}", config.companyId(), snapshot.id(), exception);
                    repository.recordProcessingResult(
                            snapshot.id(),
                            config.companyId(),
                            "FAILED",
                            config.safetyBufferPercentage(),
                            null,
                            0,
                            0,
                            0,
                            null,
                            truncate(exception.getMessage())
                    );
                }
            }
        }

        return new FxCompanyProcessingSummary(
                totalActiveCompanies,
                eligibleCompanies.size(),
                succeeded,
                failed,
                skipped,
                totalEvaluatedProducts,
                totalRecommendationsGenerated
        );
    }

    FxCompanyProcessingResult processCompany(GlobalFxRateSnapshot snapshot, FxCompanyConfig config) {
        BigDecimal effectivePricingRate = effectivePricingRate(snapshot.rate(), config.safetyBufferPercentage());
        repository.upsertCompanyEffectiveRate(snapshot.id(), config, effectivePricingRate);

        List<Integer> branchIds = repository.findActiveBranchIds(config.companyId());
        if (branchIds.isEmpty()) {
            repository.recordProcessingResult(
                    snapshot.id(),
                    config.companyId(),
                    "SKIPPED",
                    config.safetyBufferPercentage(),
                    effectivePricingRate,
                    0,
                    0,
                    0,
                    "NO_ACTIVE_BRANCHES",
                    null
            );
            return new FxCompanyProcessingResult(config.companyId(), "SKIPPED", 0, 0, 0);
        }

        List<FxProductReplacementCost> replacementCosts = repository.findFxReplacementCosts(
                config.companyId(),
                Math.max(1, properties.getRecommendationMaxProducts())
        );
        if (replacementCosts.isEmpty()) {
            repository.recordProcessingResult(
                    snapshot.id(),
                    config.companyId(),
                    "SKIPPED",
                    config.safetyBufferPercentage(),
                    effectivePricingRate,
                    0,
                    0,
                    0,
                    "NO_FX_ENABLED_PRODUCTS",
                    null
            );
            return new FxCompanyProcessingResult(config.companyId(), "SKIPPED", 0, 0, 0);
        }

        Map<Long, BigDecimal> replacementCostByProduct = new HashMap<>();
        for (FxProductReplacementCost replacementCost : replacementCosts) {
            replacementCostByProduct.put(replacementCost.productId(), replacementCost.replacementCostUsd());
        }
        List<Long> productIds = replacementCosts.stream().map(FxProductReplacementCost::productId).toList();

        int evaluatedProducts = 0;
        int recommendationRuns = 0;
        int recommendationsGenerated = 0;
        LocalDate toDate = LocalDate.now();
        int windowDays = Math.max(1, properties.getRecommendationMetricsWindowDays());

        for (Integer branchId : branchIds) {
            PricingMetricsRepository.MetricsPage metricsPage = metricsRepository.findMetrics(new PricingMetricsRepository.MetricsQuery(
                    config.companyId(),
                    branchId,
                    toDate.minusDays(windowDays - 1L),
                    toDate,
                    null,
                    productIds,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    Math.min(Math.max(1, properties.getRecommendationMaxProducts()), productIds.size())
            ));
            if (metricsPage.items().isEmpty()) {
                continue;
            }

            long runId = runRepository.createRun(
                    config.companyId(),
                    branchId,
                    windowDays,
                    "system_fx_rate",
                    scopeJson(snapshot, effectivePricingRate, config),
                    policySnapshotJson(snapshot, effectivePricingRate, config)
            );
            recommendationRuns++;

            int recommended = 0;
            int warnings = 0;
            int skipped = 0;
            for (PricingMetricsSnapshot metrics : metricsPage.items()) {
                BigDecimal replacementCostUsd = replacementCostByProduct.get(metrics.productId());
                if (replacementCostUsd == null) {
                    continue;
                }
                BigDecimal replacementCostEgp = replacementCostUsd
                        .multiply(effectivePricingRate)
                        .setScale(4, RoundingMode.HALF_UP);
                PricingMetricsSnapshot adjustedMetrics = adjustedMetrics(metrics, replacementCostEgp);
                DynamicPricingPolicy policy = policyRepository.findEffectivePolicy(
                                config.companyId(),
                                branchId,
                                metrics.productId())
                        .orElseGet(() -> policyRepository.systemDefaultPolicy(config.companyId(), branchId));
                PricingRecommendation recommendation = calculator.calculate(adjustedMetrics, policy);
                runRepository.insertItem(config.companyId(), branchId, runId, recommendation);
                repository.insertProductImpact(
                        config.companyId(),
                        branchId,
                        snapshot.id(),
                        metrics.productId(),
                        metrics.productName(),
                        replacementCostUsd,
                        effectivePricingRate,
                        replacementCostEgp,
                        metrics.buyingPrice(),
                        runId,
                        recommendation.status().name()
                );
                evaluatedProducts++;
                if (recommendation.status() == PriceRecommendationStatus.RECOMMENDED) {
                    recommended++;
                    recommendationsGenerated++;
                } else if (recommendation.status() == PriceRecommendationStatus.WARNING) {
                    warnings++;
                    recommendationsGenerated++;
                } else if (recommendation.status() == PriceRecommendationStatus.SKIPPED) {
                    skipped++;
                }
            }
            runRepository.completeRun(config.companyId(), runId, metricsPage.items().size(), recommended, warnings, skipped);
        }

        String processingStatus = evaluatedProducts == 0 ? "SKIPPED" : "SUCCESS";
        repository.recordProcessingResult(
                snapshot.id(),
                config.companyId(),
                processingStatus,
                config.safetyBufferPercentage(),
                effectivePricingRate,
                evaluatedProducts,
                recommendationRuns,
                recommendationsGenerated,
                evaluatedProducts == 0 ? "NO_BRANCH_PRODUCT_IMPACT" : null,
                null
        );
        return new FxCompanyProcessingResult(
                config.companyId(),
                processingStatus,
                evaluatedProducts,
                recommendationRuns,
                recommendationsGenerated
        );
    }

    BigDecimal effectivePricingRate(BigDecimal globalRate, BigDecimal safetyBufferPercentage) {
        BigDecimal safeBuffer = safetyBufferPercentage == null ? BigDecimal.ZERO : safetyBufferPercentage;
        return globalRate
                .multiply(BigDecimal.ONE.add(safeBuffer.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP)))
                .setScale(8, RoundingMode.HALF_UP);
    }

    private PricingMetricsSnapshot adjustedMetrics(PricingMetricsSnapshot metrics, BigDecimal replacementCostEgp) {
        BigDecimal costChangePct = metrics.buyingPrice() == null || metrics.buyingPrice().compareTo(BigDecimal.ZERO) <= 0
                ? null
                : replacementCostEgp.subtract(metrics.buyingPrice()).divide(metrics.buyingPrice(), 4, RoundingMode.HALF_UP);
        BigDecimal currentMarginPct = metrics.retailPrice() == null || metrics.retailPrice().compareTo(BigDecimal.ZERO) <= 0
                ? null
                : metrics.retailPrice().subtract(replacementCostEgp).divide(metrics.retailPrice(), 4, RoundingMode.HALF_UP);
        return new PricingMetricsSnapshot(
                metrics.productId(),
                metrics.productName(),
                metrics.category(),
                metrics.businessLineKey(),
                metrics.templateKey(),
                metrics.pricingPolicyCode(),
                metrics.supplierId(),
                metrics.retailPrice(),
                metrics.lowestPrice(),
                replacementCostEgp,
                metrics.stockQty(),
                metrics.soldQty7d(),
                metrics.soldQty30d(),
                metrics.soldQty90d(),
                metrics.salesVelocity7d(),
                metrics.salesVelocity30d(),
                metrics.salesVelocity90d(),
                metrics.weightedVelocity(),
                metrics.daysCover(),
                currentMarginPct,
                metrics.movementClass(),
                metrics.demandScore(),
                costChangePct,
                metrics.lastSaleAt()
        );
    }

    private String scopeJson(GlobalFxRateSnapshot snapshot,
                             BigDecimal effectivePricingRate,
                             FxCompanyConfig config) {
        return """
                {"source":"GLOBAL_FX_RATE","globalFxSnapshotId":%d,"baseCurrency":"%s","targetCurrency":"%s","globalRate":%s,"companyEffectiveRate":%s,"safetyBufferPercentage":%s}
                """.formatted(
                snapshot.id(),
                json(snapshot.baseCurrency()),
                json(snapshot.targetCurrency()),
                number(snapshot.rate()),
                number(effectivePricingRate),
                number(config.safetyBufferPercentage())
        ).trim();
    }

    private String policySnapshotJson(GlobalFxRateSnapshot snapshot,
                                      BigDecimal effectivePricingRate,
                                      FxCompanyConfig config) {
        return """
                {"mode":"fx_replacement_cost","calculator":"deterministic_v1","globalFxSnapshotId":%d,"selectedRateType":"%s","effectivePricingRate":%s,"automaticPriceApply":false}
                """.formatted(
                snapshot.id(),
                json(config.selectedRateType()),
                number(effectivePricingRate)
        ).trim();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String number(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown company FX processing failure";
        }
        String normalized = value.trim().replace("\n", " ").replace("\r", " ");
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }
}
