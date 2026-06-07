package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceReasonCode;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceRecommendationStatus;
import com.example.valueinsoftbackend.pricing.dynamic.model.PricingMetricsSnapshot;
import com.example.valueinsoftbackend.pricing.dynamic.model.PricingRecommendation;
import com.example.valueinsoftbackend.pricing.dynamic.model.ProductMovementClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PriceRecommendationCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal THREE_PERCENT = new BigDecimal("0.0300");
    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.0500");

    public PricingRecommendation calculate(PricingMetricsSnapshot metrics, DynamicPricingPolicy policy) {
        List<PriceReasonCode> reasons = new ArrayList<>();
        List<PriceReasonCode> warnings = new ArrayList<>();

        BigDecimal currentRetail = money(metrics.retailPrice());
        BigDecimal currentLowest = money(metrics.lowestPrice());
        BigDecimal buyingPrice = money(metrics.buyingPrice());

        if (currentRetail.compareTo(BigDecimal.ZERO) <= 0 || buyingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add(PriceReasonCode.INSUFFICIENT_DATA);
            return build(metrics, policy, currentRetail, currentLowest, ZERO, null,
                    PriceRecommendationStatus.SKIPPED, true, reasons, warnings);
        }

        if (metrics.stockQty() != null && metrics.stockQty().compareTo(BigDecimal.ZERO) <= 0) {
            warnings.add(PriceReasonCode.ZERO_STOCK);
        }

        BigDecimal targetMarginPrice = targetMarginPrice(buyingPrice, policy.targetMarginPct());
        BigDecimal candidate = currentRetail.max(targetMarginPrice);
        if (candidate.compareTo(currentRetail) > 0) {
            reasons.add(PriceReasonCode.TARGET_MARGIN_ALIGNMENT);
            if (metrics.currentMarginPct() != null && metrics.currentMarginPct().compareTo(policy.minMarginPct()) < 0) {
                reasons.add(PriceReasonCode.MIN_MARGIN_RECOVERY);
            }
        }

        if (metrics.costChangePct() != null && metrics.costChangePct().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal boundedCostIncrease = metrics.costChangePct().min(policy.maxIncreasePct());
            BigDecimal costCandidate = currentRetail.multiply(ONE.add(boundedCostIncrease));
            if (costCandidate.compareTo(candidate) > 0) {
                candidate = costCandidate;
                reasons.add(PriceReasonCode.COST_INCREASE);
            }
        }

        if (metrics.movementClass() == ProductMovementClass.FAST
                && metrics.daysCover() != null
                && metrics.daysCover().compareTo(policy.lowStockDaysCover()) <= 0) {
            BigDecimal demandIncrease = policy.maxIncreasePct().min(THREE_PERCENT);
            candidate = candidate.max(currentRetail.multiply(ONE.add(demandIncrease)));
            reasons.add(PriceReasonCode.LOW_STOCK_HIGH_DEMAND);
        }

        if ((metrics.movementClass() == ProductMovementClass.SLOW || metrics.movementClass() == ProductMovementClass.DEAD)
                && metrics.daysCover() != null
                && metrics.daysCover().compareTo(policy.overstockDaysCover()) >= 0) {
            BigDecimal decreasePct = metrics.movementClass() == ProductMovementClass.DEAD
                    ? policy.maxDecreasePct().min(FIVE_PERCENT)
                    : policy.maxDecreasePct().min(THREE_PERCENT);
            BigDecimal movementCandidate = currentRetail.multiply(ONE.subtract(decreasePct));
            candidate = candidate.min(movementCandidate);
            reasons.add(metrics.movementClass() == ProductMovementClass.DEAD
                    ? PriceReasonCode.DEAD_STOCK_CLEARANCE
                    : PriceReasonCode.OVERSTOCK_SLOW_MOVING);
        }

        BigDecimal capped = applyCaps(currentRetail, candidate, policy, reasons, warnings);
        BigDecimal minMarginPrice = targetMarginPrice(buyingPrice, policy.minMarginPct());
        if (!policy.allowBelowCost()) {
            capped = capped.max(buyingPrice).max(minMarginPrice);
        }
        if (policy.minFinalPrice() != null) {
            capped = capped.max(policy.minFinalPrice());
        }
        if (policy.maxFinalPrice() != null && capped.compareTo(policy.maxFinalPrice()) > 0) {
            capped = policy.maxFinalPrice();
            warnings.add(PriceReasonCode.POLICY_CAP_APPLIED);
        }

        BigDecimal roundedRetail = round(capped, policy.roundingMode());
        if (roundedRetail.compareTo(capped) != 0) {
            reasons.add(PriceReasonCode.ROUNDING_APPLIED);
        }

        BigDecimal suggestedLowest = currentLowest;
        BigDecimal minimumLowest = policy.allowBelowCost() ? ZERO : buyingPrice.max(minMarginPrice);
        if (suggestedLowest.compareTo(minimumLowest) < 0) {
            suggestedLowest = minimumLowest.min(roundedRetail);
        }
        if (suggestedLowest.compareTo(roundedRetail) > 0) {
            suggestedLowest = roundedRetail;
        }

        PriceRecommendationStatus status;
        if (roundedRetail.compareTo(currentRetail) == 0 && suggestedLowest.compareTo(currentLowest) == 0) {
            status = warnings.isEmpty() ? PriceRecommendationStatus.NO_CHANGE : PriceRecommendationStatus.WARNING;
            reasons.add(PriceReasonCode.NO_CHANGE_WITHIN_POLICY);
        } else {
            status = warnings.isEmpty() ? PriceRecommendationStatus.RECOMMENDED : PriceRecommendationStatus.WARNING;
        }

        BigDecimal suggestedMargin = roundedRetail.compareTo(BigDecimal.ZERO) > 0
                ? roundedRetail.subtract(buyingPrice).divide(roundedRetail, 4, RoundingMode.HALF_UP)
                : null;
        return build(metrics, policy, roundedRetail, suggestedLowest, roundedRetail.subtract(currentRetail),
                suggestedMargin, status, policy.approvalRequired() || !warnings.isEmpty(), reasons, warnings);
    }

    private BigDecimal applyCaps(BigDecimal currentRetail, BigDecimal candidate, DynamicPricingPolicy policy,
                                 List<PriceReasonCode> reasons, List<PriceReasonCode> warnings) {
        BigDecimal maxIncreasePrice = currentRetail.multiply(ONE.add(policy.maxIncreasePct()));
        BigDecimal maxDecreasePrice = currentRetail.multiply(ONE.subtract(policy.maxDecreasePct()));
        BigDecimal result = candidate;

        if (policy.maxIncreaseAmount() != null) {
            maxIncreasePrice = maxIncreasePrice.min(currentRetail.add(policy.maxIncreaseAmount()));
        }
        if (policy.maxDecreaseAmount() != null) {
            maxDecreasePrice = maxDecreasePrice.max(currentRetail.subtract(policy.maxDecreaseAmount()));
        }

        if (candidate.compareTo(maxIncreasePrice) > 0) {
            result = maxIncreasePrice;
            reasons.add(PriceReasonCode.POLICY_CAP_APPLIED);
            warnings.add(PriceReasonCode.MAX_INCREASE_BLOCKED);
        }
        if (candidate.compareTo(maxDecreasePrice) < 0) {
            result = maxDecreasePrice;
            reasons.add(PriceReasonCode.POLICY_CAP_APPLIED);
            warnings.add(PriceReasonCode.MAX_DECREASE_BLOCKED);
        }
        return result;
    }

    private PricingRecommendation build(PricingMetricsSnapshot metrics, DynamicPricingPolicy policy,
                                        BigDecimal suggestedRetail, BigDecimal suggestedLowest,
                                        BigDecimal deltaAmount, BigDecimal suggestedMargin,
                                        PriceRecommendationStatus status, boolean approvalRequired,
                                        List<PriceReasonCode> reasons, List<PriceReasonCode> warnings) {
        BigDecimal deltaPct = metrics.retailPrice() == null || metrics.retailPrice().compareTo(BigDecimal.ZERO) <= 0
                ? ZERO
                : deltaAmount.divide(metrics.retailPrice(), 4, RoundingMode.HALF_UP);
        return new PricingRecommendation(
                metrics,
                policy,
                money(suggestedRetail),
                money(suggestedLowest),
                money(deltaAmount),
                deltaPct,
                suggestedMargin,
                status,
                approvalRequired,
                reasons.stream().distinct().toList(),
                warnings.stream().distinct().toList(),
                explanationJson(metrics, policy, suggestedRetail, suggestedLowest, reasons, warnings)
        );
    }

    private String explanationJson(PricingMetricsSnapshot metrics, DynamicPricingPolicy policy,
                                   BigDecimal suggestedRetail, BigDecimal suggestedLowest,
                                   List<PriceReasonCode> reasons, List<PriceReasonCode> warnings) {
        return String.format(Locale.ROOT, """
                {"inputs":{"currentRetailPrice":%s,"currentLowestPrice":%s,"buyingPrice":%s,"stockQty":%s,"daysCover":%s,"movementClass":"%s","targetMarginPct":%s,"minMarginPct":%s},"result":{"suggestedRetailPrice":%s,"suggestedLowestPrice":%s},"reasonCodes":%s,"warningCodes":%s}
                """,
                jsonNumber(metrics.retailPrice()),
                jsonNumber(metrics.lowestPrice()),
                jsonNumber(metrics.buyingPrice()),
                jsonNumber(metrics.stockQty()),
                jsonNumber(metrics.daysCover()),
                metrics.movementClass().name(),
                jsonNumber(policy.targetMarginPct()),
                jsonNumber(policy.minMarginPct()),
                jsonNumber(suggestedRetail),
                jsonNumber(suggestedLowest),
                jsonArray(reasons),
                jsonArray(warnings)
        ).trim();
    }

    private BigDecimal targetMarginPrice(BigDecimal buyingPrice, BigDecimal marginPct) {
        BigDecimal divisor = ONE.subtract(marginPct);
        if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
            return buyingPrice;
        }
        return buyingPrice.divide(divisor, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal round(BigDecimal value, String roundingMode) {
        BigDecimal increment = switch (roundingMode == null ? "NEAREST_1" : roundingMode) {
            case "NEAREST_5" -> new BigDecimal("5");
            case "NEAREST_10" -> new BigDecimal("10");
            default -> BigDecimal.ONE;
        };
        if ("CEILING_1".equals(roundingMode)) {
            return value.setScale(0, RoundingMode.CEILING).setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal divided = value.divide(increment, 0, RoundingMode.HALF_UP);
        return divided.multiply(increment).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private String jsonNumber(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private String jsonArray(List<PriceReasonCode> values) {
        return values.stream().distinct()
                .map(value -> "\"" + value.name() + "\"")
                .toList()
                .toString();
    }
}
