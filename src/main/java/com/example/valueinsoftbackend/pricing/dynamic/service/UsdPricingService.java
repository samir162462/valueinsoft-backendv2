package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingCostUpdateRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingProductRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingProductResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingProductsPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingRateResponse;
import com.example.valueinsoftbackend.pricing.dynamic.repository.UsdPricingRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class UsdPricingService {

    private static final BigDecimal DEFAULT_TARGET_MARGIN = new BigDecimal("0.2000");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final UsdPricingRepository repository;
    private final DynamicPricingSecurityService securityService;
    private final PricingAuditService auditService;

    public UsdPricingService(UsdPricingRepository repository,
                             DynamicPricingSecurityService securityService,
                             PricingAuditService auditService) {
        this.repository = repository;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    public UsdPricingProductsPageResponse listProducts(String actorName, UsdPricingProductRequest request) {
        securityService.requireView(actorName, request.companyId(), request.branchId());
        repository.ensureLatestCompanyRate(request.companyId());
        repository.backfillMissingUsdCosts(request.companyId());
        boolean includeCost = securityService.canReadCost(actorName, request.companyId(), request.branchId());
        UsdPricingRepository.ProductsPage page = repository.findProducts(request);
        BigDecimal targetMarginPct = targetMargin(request.targetMarginPct());

        List<UsdPricingProductResponse> items = page.items().stream()
                .map(row -> map(row, includeCost, targetMarginPct))
                .toList();

        UsdPricingRepository.RateRow rate = page.items().stream()
                .filter(row -> row.globalFxSnapshotId() != null)
                .findFirst()
                .map(row -> new UsdPricingRepository.RateRow(
                        row.globalFxSnapshotId(),
                        row.globalRate(),
                        row.effectivePricingRate(),
                        row.safetyBufferPercentage(),
                        row.selectedRateType(),
                        row.effectiveDate(),
                        row.calculationTimestamp()
                ))
                .orElseGet(() -> repository.findLatestCompanyRate(request.companyId()).orElse(null));

        return new UsdPricingProductsPageResponse(
                page.page(),
                page.size(),
                page.totalItems(),
                page.totalPages(),
                includeCost,
                rate == null ? null : rate.globalFxSnapshotId(),
                includeCost && rate != null ? rate.globalRate() : null,
                includeCost && rate != null ? rate.effectivePricingRate() : null,
                includeCost && rate != null ? rate.safetyBufferPercentage() : null,
                rate == null ? null : rate.selectedRateType(),
                rate == null ? null : rate.effectiveDate(),
                rate == null ? null : rate.calculationTimestamp(),
                items
        );
    }

    public UsdPricingRateResponse currentRate(String actorName, int companyId, int branchId) {
        securityService.requireView(actorName, companyId, branchId);
        repository.ensureLatestCompanyRate(companyId);
        boolean includeCost = securityService.canReadCost(actorName, companyId, branchId);
        UsdPricingRepository.RateRow rate = repository.findLatestCompanyRate(companyId).orElse(null);
        return new UsdPricingRateResponse(
                rate == null ? null : rate.globalFxSnapshotId(),
                includeCost && rate != null ? rate.globalRate() : null,
                includeCost && rate != null ? rate.effectivePricingRate() : null,
                includeCost && rate != null ? rate.safetyBufferPercentage() : null,
                rate == null ? null : rate.selectedRateType(),
                rate == null ? null : rate.effectiveDate(),
                rate == null ? null : rate.calculationTimestamp()
        );
    }

    @Transactional
    public UsdPricingProductResponse updateCost(String actorName, long productId, UsdPricingCostUpdateRequest request) {
        securityService.requireUsdCostEdit(actorName, request.companyId(), request.branchId());
        if (Boolean.TRUE.equals(request.fxPricingEnabled())
                && (request.replacementCostUsd() == null || request.replacementCostUsd().compareTo(ZERO) <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USD_REPLACEMENT_COST_REQUIRED", "replacementCostUsd is required when FX pricing is enabled");
        }
        if (Boolean.TRUE.equals(request.fxPricingEnabled())
                && (request.purchaseUsdRate() == null || request.purchaseUsdRate().compareTo(ZERO) <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USD_PURCHASE_RATE_REQUIRED", "purchaseUsdRate is required when FX pricing is enabled");
        }

        UsdPricingRepository.ProductCostRow before = repository.findProductCost(request.companyId(), productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found"));

        BigDecimal normalizedCost = Boolean.TRUE.equals(request.fxPricingEnabled())
                ? money(request.replacementCostUsd())
                : null;
        BigDecimal normalizedPurchaseRate = Boolean.TRUE.equals(request.fxPricingEnabled())
                ? fxRate(request.purchaseUsdRate())
                : null;
        repository.updateUsdCost(request.companyId(), productId, request.fxPricingEnabled(), normalizedCost, normalizedPurchaseRate);
        repository.ensureLatestCompanyRate(request.companyId());

        UsdPricingRepository.ProductCostRow after = repository.findProductCost(request.companyId(), productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found after update"));
        auditService.log(
                request.companyId(),
                request.branchId(),
                "USD_COST_UPDATED",
                "PRODUCT_PRICE",
                String.valueOf(productId),
                actorName,
                "USD replacement cost updated for " + after.productName(),
                costAuditJson(before, after, request.note())
        );

        return repository.findProduct(request.companyId(), request.branchId(), productId)
                .map(row -> map(row, true, DEFAULT_TARGET_MARGIN))
                .orElse(null);
    }

    private UsdPricingProductResponse map(UsdPricingRepository.ProductRow row, boolean includeCost, BigDecimal targetMarginPct) {
        BigDecimal convertedCost = convertedCost(row);
        BigDecimal purchaseCostLocal = purchaseCostLocal(row);
        BigDecimal currentRetail = money(row.currentRetailPrice());
        BigDecimal currentLowest = money(row.currentLowestPrice());
        BigDecimal profit = convertedCost == null || currentRetail == null ? null : money(currentRetail.subtract(convertedCost));
        BigDecimal margin = profit == null || currentRetail == null || currentRetail.compareTo(ZERO) <= 0
                ? null
                : profit.divide(currentRetail, 4, RoundingMode.HALF_UP);
        BigDecimal markup = profit == null || convertedCost == null || convertedCost.compareTo(ZERO) <= 0
                ? null
                : profit.divide(convertedCost, 4, RoundingMode.HALF_UP);
        BigDecimal suggestedRetail = suggestedRetail(convertedCost, currentRetail, targetMarginPct);
        BigDecimal suggestedLowest = suggestedRetail == null || currentLowest == null
                ? suggestedRetail
                : currentLowest.min(suggestedRetail);
        BigDecimal delta = suggestedRetail == null || currentRetail == null ? null : money(suggestedRetail.subtract(currentRetail));
        BigDecimal deltaPct = delta == null || currentRetail == null || currentRetail.compareTo(ZERO) <= 0
                ? null
                : delta.divide(currentRetail, 4, RoundingMode.HALF_UP);
        String status = status(row, convertedCost, currentRetail, margin, targetMarginPct);

        return new UsdPricingProductResponse(
                row.productId(),
                row.productName(),
                row.category(),
                row.businessLineKey(),
                row.templateKey(),
                row.pricingPolicyCode(),
                row.supplierId(),
                row.serial(),
                row.barcode(),
                row.stockQty(),
                row.fxPricingEnabled(),
                includeCost ? row.replacementCostUsd() : null,
                includeCost ? row.replacementCostCurrency() : null,
                includeCost ? row.purchaseUsdRate() : null,
                includeCost ? row.replacementCostUpdatedAt() : null,
                row.globalFxSnapshotId(),
                includeCost ? row.globalRate() : null,
                includeCost ? row.effectivePricingRate() : null,
                includeCost ? row.safetyBufferPercentage() : null,
                row.selectedRateType(),
                row.effectiveDate(),
                includeCost ? purchaseCostLocal : null,
                includeCost ? convertedCost : null,
                includeCost ? row.currentBuyingPrice() : null,
                currentRetail,
                currentLowest,
                includeCost ? profit : null,
                includeCost ? margin : null,
                includeCost ? markup : null,
                includeCost ? suggestedRetail : null,
                includeCost ? suggestedLowest : null,
                includeCost ? delta : null,
                includeCost ? deltaPct : null,
                status
        );
    }

    private BigDecimal convertedCost(UsdPricingRepository.ProductRow row) {
        if (!row.fxPricingEnabled()
                || row.replacementCostUsd() == null
                || row.replacementCostUsd().compareTo(ZERO) <= 0
                || row.effectivePricingRate() == null
                || row.effectivePricingRate().compareTo(ZERO) <= 0) {
            return null;
        }
        return money(row.replacementCostUsd().multiply(row.effectivePricingRate()));
    }

    private BigDecimal purchaseCostLocal(UsdPricingRepository.ProductRow row) {
        if (!row.fxPricingEnabled()
                || row.replacementCostUsd() == null
                || row.replacementCostUsd().compareTo(ZERO) <= 0
                || row.purchaseUsdRate() == null
                || row.purchaseUsdRate().compareTo(ZERO) <= 0) {
            return null;
        }
        return money(row.replacementCostUsd().multiply(row.purchaseUsdRate()));
    }

    private BigDecimal suggestedRetail(BigDecimal convertedCost, BigDecimal currentRetail, BigDecimal targetMarginPct) {
        if (convertedCost == null || convertedCost.compareTo(ZERO) <= 0) {
            return null;
        }
        BigDecimal denominator = ONE.subtract(targetMarginPct);
        if (denominator.compareTo(ZERO) <= 0) {
            return null;
        }
        BigDecimal targetPrice = convertedCost.divide(denominator, 4, RoundingMode.HALF_UP);
        BigDecimal base = currentRetail == null ? targetPrice : targetPrice.max(currentRetail);
        return base.setScale(0, RoundingMode.CEILING);
    }

    private String status(UsdPricingRepository.ProductRow row,
                          BigDecimal convertedCost,
                          BigDecimal currentRetail,
                          BigDecimal margin,
                          BigDecimal targetMarginPct) {
        if (row.globalFxSnapshotId() == null || row.effectivePricingRate() == null) {
            return "NO_RATE";
        }
        if (!row.fxPricingEnabled()) {
            return "FX_DISABLED";
        }
        if (row.replacementCostUsd() == null || row.replacementCostUsd().compareTo(ZERO) <= 0) {
            return "MISSING_USD_COST";
        }
        if (convertedCost == null || currentRetail == null || currentRetail.compareTo(ZERO) <= 0) {
            return "INCOMPLETE_PRICE";
        }
        if (currentRetail.compareTo(convertedCost) <= 0) {
            return "BELOW_REPLACEMENT_COST";
        }
        if (margin != null && margin.compareTo(targetMarginPct) < 0) {
            return "LOW_MARGIN";
        }
        return "OK";
    }

    private BigDecimal targetMargin(BigDecimal requested) {
        if (requested == null) {
            return DEFAULT_TARGET_MARGIN;
        }
        if (requested.compareTo(ZERO) < 0 || requested.compareTo(new BigDecimal("0.9500")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_MARGIN_INVALID", "targetMarginPct must be between 0 and 0.95");
        }
        return requested;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal fxRate(BigDecimal value) {
        return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
    }

    private String costAuditJson(UsdPricingRepository.ProductCostRow before,
                                 UsdPricingRepository.ProductCostRow after,
                                 String note) {
        return """
                {"before":{"fxPricingEnabled":%s,"replacementCostUsd":%s,"replacementCostCurrency":"%s","purchaseUsdRate":%s},"after":{"fxPricingEnabled":%s,"replacementCostUsd":%s,"replacementCostCurrency":"%s","purchaseUsdRate":%s},"note":"%s"}
                """.formatted(
                before.fxPricingEnabled(),
                number(before.replacementCostUsd()),
                json(before.replacementCostCurrency()),
                number(before.purchaseUsdRate()),
                after.fxPricingEnabled(),
                number(after.replacementCostUsd()),
                json(after.replacementCostCurrency()),
                number(after.purchaseUsdRate()),
                json(note)
        ).trim();
    }

    private String number(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
