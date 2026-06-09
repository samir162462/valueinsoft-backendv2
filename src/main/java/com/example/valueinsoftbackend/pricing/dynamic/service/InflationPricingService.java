package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.*;
import com.example.valueinsoftbackend.pricing.dynamic.repository.InflationPricingRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class InflationPricingService {

    private final InflationPricingRepository repository;
    private final DynamicPricingSecurityService securityService;
    private final PricingAuditService auditService;

    public InflationPricingService(InflationPricingRepository repository,
                                   DynamicPricingSecurityService securityService,
                                   PricingAuditService auditService) {
        this.repository = repository;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    public InflationPreviewResponse preview(String actorName, InflationPreviewRequest request) {
        securityService.requireAdjustmentPreview(actorName, request.companyId(), request.branchId());

        if (!request.adjustBuying() && !request.adjustRetail() && !request.adjustLowest()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_INFLATION_NO_TARGETS",
                    "At least one price target (Buying, Retail, or Lowest) must be selected for adjustment");
        }

        List<InflationPricingRepository.ProductRow> products = repository.findProductsForInflation(
                request.companyId(),
                request.branchId(),
                request.scopeType(),
                request.scopeValue(),
                250
        );

        boolean isDecrease = "DECREASE".equalsIgnoreCase(request.direction());
        BigDecimal rateMultiplier = "PERCENTAGE".equalsIgnoreCase(request.mode())
                ? request.rate().divide(new BigDecimal("100.00"), 4, RoundingMode.HALF_UP)
                : request.rate();
        if (isDecrease) {
            rateMultiplier = rateMultiplier.negate();
        }

        List<InflationPreviewItem> items = new ArrayList<>();
        for (var p : products) {
            BigDecimal oldBuying = p.buyingPrice() != null ? p.buyingPrice() : BigDecimal.ZERO;
            BigDecimal oldRetail = p.retailPrice() != null ? p.retailPrice() : BigDecimal.ZERO;
            BigDecimal oldLowest = p.lowestPrice() != null ? p.lowestPrice() : BigDecimal.ZERO;

            BigDecimal newBuying = request.adjustBuying() ? adjust(oldBuying, rateMultiplier, request.mode()) : oldBuying;
            BigDecimal newRetail = request.adjustRetail() ? adjust(oldRetail, rateMultiplier, request.mode()) : oldRetail;
            BigDecimal newLowest = request.adjustLowest() ? adjust(oldLowest, rateMultiplier, request.mode()) : oldLowest;

            // Clamp to enforce buying <= lowest <= retail constraint
            newBuying = newBuying.max(BigDecimal.ZERO);
            newLowest = newLowest.max(newBuying);
            newRetail = newRetail.max(newLowest);

            items.add(new InflationPreviewItem(
                    p.productId(),
                    p.productName(),
                    oldBuying,
                    newBuying,
                    oldRetail,
                    newRetail,
                    oldLowest,
                    newLowest
            ));
        }

        return new InflationPreviewResponse(products.size(), items);
    }

    @Transactional
    public InflationApplyResponse apply(String actorName, InflationApplyRequest request) {
        securityService.requireAdjustmentApply(actorName, request.companyId(), request.branchId());

        if (!request.adjustBuying() && !request.adjustRetail() && !request.adjustLowest()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_INFLATION_NO_TARGETS",
                    "At least one price target (Buying, Retail, or Lowest) must be selected for adjustment");
        }

        boolean isDecrease = "DECREASE".equalsIgnoreCase(request.direction());
        BigDecimal rateMultiplier = "PERCENTAGE".equalsIgnoreCase(request.mode())
                ? request.rate().divide(new BigDecimal("100.00"), 4, RoundingMode.HALF_UP)
                : request.rate();
        if (isDecrease) {
            rateMultiplier = rateMultiplier.negate();
        }

        int updatedCount = repository.applyBulkInflation(
                request.companyId(),
                request.branchId(),
                request.scopeType(),
                request.scopeValue(),
                rateMultiplier,
                request.adjustBuying(),
                request.adjustRetail(),
                request.adjustLowest(),
                request.mode(),
                request.roundingFactor(),
                request.productIds()
        );

        auditService.log(request.companyId(), request.branchId(), "BULK_INFLATION_APPLIED", "CATALOG",
                "BULK", actorName, "Applied bulk inflation price adjustments",
                String.format("{\"scopeType\":\"%s\",\"mode\":\"%s\",\"direction\":\"%s\",\"rate\":%s,\"updatedCount\":%d}",
                        request.scopeType(), request.mode(),
                        request.direction() != null ? request.direction() : "INCREASE",
                        request.rate().toString(), updatedCount));

        return new InflationApplyResponse(updatedCount);
    }

    private BigDecimal adjust(BigDecimal current, BigDecimal rate, String mode) {
        BigDecimal result = current;
        if ("PERCENTAGE".equalsIgnoreCase(mode)) {
            result = current.multiply(BigDecimal.ONE.add(rate));
        } else {
            result = current.add(rate);
        }
        return result.setScale(4, RoundingMode.HALF_UP);
    }
}
