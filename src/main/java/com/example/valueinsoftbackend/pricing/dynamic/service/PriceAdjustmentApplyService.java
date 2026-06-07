package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApplyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApplyResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchResponse;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceAdjustmentBatchRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceHistoryRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PriceAdjustmentApplyService {

    private final PriceAdjustmentBatchRepository batchRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final DynamicPricingSecurityService securityService;
    private final PricingAuditService auditService;

    public PriceAdjustmentApplyService(PriceAdjustmentBatchRepository batchRepository,
                                       PriceHistoryRepository priceHistoryRepository,
                                       DynamicPricingSecurityService securityService,
                                       PricingAuditService auditService) {
        this.batchRepository = batchRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    @Transactional
    public PriceAdjustmentApplyResponse apply(String actorName, int companyId, int branchId, long batchId,
                                              PriceAdjustmentApplyRequest request) {
        securityService.requireAdjustmentApply(actorName, companyId, branchId);
        PriceAdjustmentBatchResponse batch = batchRepository.lockBatch(companyId, batchId);
        if (batch.branchId() != branchId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRICING_ADJUSTMENT_BATCH_NOT_FOUND",
                    "Adjustment batch was not found for this branch");
        }
        if (!"APPROVED".equals(batch.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICING_ADJUSTMENT_APPLY_STATUS_INVALID",
                    "Only approved adjustment batches can be applied");
        }

        batchRepository.markApplying(companyId, batchId, actorName);
        auditService.log(companyId, branchId, "ADJUSTMENT_BATCH_APPLY_STARTED", "BATCH", String.valueOf(batchId),
                actorName, "Started applying price adjustment batch", "{\"batchId\":" + batchId + "}");

        List<PriceAdjustmentBatchRepository.ApplyItemRow> items = batchRepository.findApplicableItems(companyId, branchId, batchId);
        int applied = 0;
        int skipped = 0;
        int failed = 0;

        for (var item : items) {
            try {
                PriceAdjustmentBatchRepository.ProductPriceRow current = batchRepository.findProductPrice(companyId, item.productId());
                if (current == null
                        || current.retailPrice().compareTo(item.oldRetailPrice()) != 0
                        || current.lowestPrice().compareTo(item.oldLowestPrice()) != 0) {
                    skipped++;
                    batchRepository.markItemSkipped(companyId, item.itemId(), "Product price changed after preview");
                    continue;
                }
                String guardrailMessage = applyGuardrailMessage(current, item);
                if (guardrailMessage != null) {
                    skipped++;
                    batchRepository.markItemSkipped(companyId, item.itemId(), guardrailMessage);
                    continue;
                }

                int affected = batchRepository.updateProductPriceIfCurrent(companyId, item);
                if (affected == 0) {
                    skipped++;
                    batchRepository.markItemSkipped(companyId, item.itemId(), "Product price changed before update");
                    continue;
                }

                priceHistoryRepository.insertAppliedPriceChange(companyId, branchId, batchId, item, actorName,
                        request == null ? batch.reason() : firstText(request.note(), batch.reason()));
                batchRepository.markItemApplied(companyId, item.itemId());
                auditService.log(companyId, branchId, "PRODUCT_PRICE_UPDATED", "PRODUCT_PRICE", String.valueOf(item.productId()),
                        actorName, "Updated product price through approved adjustment batch",
                        "{\"batchId\":" + batchId + ",\"productId\":" + item.productId() + "}");
                applied++;
            } catch (Exception exception) {
                failed++;
                batchRepository.markItemFailed(companyId, item.itemId(), truncate(exception.getMessage()));
            }
        }

        String finalStatus;
        if (applied > 0 && failed == 0 && skipped == 0) {
            finalStatus = "APPLIED";
        } else if (applied > 0) {
            finalStatus = "PARTIALLY_APPLIED";
        } else {
            finalStatus = "FAILED";
        }
        batchRepository.completeApply(companyId, batchId, finalStatus, applied, failed + skipped);
        auditService.log(companyId, branchId, finalStatus.equals("APPLIED")
                        ? "ADJUSTMENT_BATCH_APPLIED"
                        : finalStatus.equals("PARTIALLY_APPLIED")
                        ? "ADJUSTMENT_BATCH_PARTIALLY_APPLIED"
                        : "ADJUSTMENT_BATCH_FAILED",
                "BATCH", String.valueOf(batchId), actorName, "Finished applying price adjustment batch",
                "{\"batchId\":" + batchId + ",\"applied\":" + applied + ",\"skipped\":" + skipped + ",\"failed\":" + failed + "}");

        return new PriceAdjustmentApplyResponse(
                batchId,
                finalStatus,
                applied,
                skipped,
                failed,
                "Adjustment batch apply finished"
        );
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second;
    }

    private String applyGuardrailMessage(PriceAdjustmentBatchRepository.ProductPriceRow current,
                                         PriceAdjustmentBatchRepository.ApplyItemRow item) {
        if (item.newRetailPrice().compareTo(item.newLowestPrice()) < 0) {
            return "Retail price would be lower than lowest price";
        }
        if (item.newRetailPrice().compareTo(current.buyingPrice()) < 0
                || item.newLowestPrice().compareTo(current.buyingPrice()) < 0) {
            return "New price is below current buying price";
        }
        return null;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Apply failed";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
