package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchesPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentCreateRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentItemsPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentPreviewRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentPreviewResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.RecommendationAdjustmentCreateRequest;
import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceAdjustmentDirection;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceAdjustmentMode;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceTarget;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceAdjustmentBatchRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BulkPriceAdjustmentService {

    private static final int DEFAULT_MAX_PRODUCTS = 100;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    private final PriceAdjustmentBatchRepository repository;
    private final DynamicPricingPolicyRepository policyRepository;
    private final DynamicPricingSecurityService securityService;
    private final PricingAuditService auditService;

    public BulkPriceAdjustmentService(PriceAdjustmentBatchRepository repository,
                                      DynamicPricingPolicyRepository policyRepository,
                                      DynamicPricingSecurityService securityService,
                                      PricingAuditService auditService) {
        this.repository = repository;
        this.policyRepository = policyRepository;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    public PriceAdjustmentPreviewResponse preview(String actorName, PriceAdjustmentPreviewRequest request) {
        securityService.requireAdjustmentPreview(actorName, request.companyId(), request.branchId());
        return createManualBatch(actorName, request, "ADJUSTMENT_PREVIEW_CREATED");
    }

    public PriceAdjustmentPreviewResponse create(String actorName, PriceAdjustmentCreateRequest request) {
        if (request.preview() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_REQUEST_INVALID",
                    "Adjustment create request must include preview details");
        }
        securityService.requireAdjustmentCreate(actorName, request.preview().companyId(), request.preview().branchId());
        return createManualBatch(actorName, request.preview(), "ADJUSTMENT_BATCH_CREATED");
    }

    public PriceAdjustmentPreviewResponse createFromRecommendation(String actorName, long runId,
                                                                   RecommendationAdjustmentCreateRequest request) {
        securityService.requireAdjustmentCreate(actorName, request.companyId(), request.branchId());
        int maxProducts = request.maxProducts() == null ? DEFAULT_MAX_PRODUCTS : request.maxProducts();
        List<PriceAdjustmentBatchRepository.AdjustmentProductRow> products = repository.findRecommendationRows(
                request.companyId(),
                request.branchId(),
                runId,
                request.recommendationItemIds(),
                maxProducts
        );

        long batchId = repository.createBatch(
                request.companyId(),
                request.branchId(),
                "RECOMMENDATION",
                runId,
                "PREVIEWED",
                PriceAdjustmentMode.RECOMMENDED_PRICE.name(),
                null,
                null,
                priceTargetJson(PriceTarget.RETAIL_AND_LOWEST),
                "{\"runId\":" + runId + ",\"recommendationItems\":" + (request.recommendationItemIds() == null ? 0 : request.recommendationItemIds().size()) + "}",
                actorName,
                request.reason()
        );

        Counts counts = insertDrafts(request.companyId(), request.branchId(), batchId, products, null, null,
                PriceTarget.RETAIL_AND_LOWEST, true);
        repository.updateCounts(request.companyId(), batchId, counts.total(), counts.valid(), counts.warning(), counts.blocked());
        auditService.log(request.companyId(), request.branchId(), "RECOMMENDATION_BATCH_CREATED", "BATCH",
                String.valueOf(batchId), actorName, "Created price adjustment preview from recommendations",
                "{\"batchId\":" + batchId + ",\"runId\":" + runId + "}");

        return response(actorName, request.companyId(), request.branchId(), batchId);
    }

    public PriceAdjustmentBatchResponse getBatch(String actorName, int companyId, int branchId, long batchId) {
        securityService.requireView(actorName, companyId, branchId);
        PriceAdjustmentBatchResponse batch = repository.findBatch(companyId, batchId);
        if (batch.branchId() != branchId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRICING_ADJUSTMENT_BATCH_NOT_FOUND",
                    "Adjustment batch was not found for this branch");
        }
        return batch;
    }

    public PriceAdjustmentBatchesPageResponse getBatches(String actorName, int companyId, int branchId,
                                                         String status, int page, int size) {
        securityService.requireView(actorName, companyId, branchId);
        PriceAdjustmentBatchRepository.BatchesPage batchesPage = repository.findBatches(
                companyId,
                branchId,
                normalizeBatchStatus(status),
                page,
                size
        );
        return new PriceAdjustmentBatchesPageResponse(
                batchesPage.page(),
                batchesPage.size(),
                batchesPage.totalItems(),
                batchesPage.totalPages(),
                batchesPage.items()
        );
    }

    public PriceAdjustmentItemsPageResponse getItems(String actorName, int companyId, int branchId, long batchId,
                                                     String status, int page, int size) {
        securityService.requireView(actorName, companyId, branchId);
        boolean includeCost = securityService.canReadCost(actorName, companyId, branchId);
        PriceAdjustmentBatchRepository.ItemsPage itemsPage = repository.findItems(
                companyId,
                branchId,
                batchId,
                normalizeItemStatus(status),
                page,
                size,
                includeCost
        );
        return new PriceAdjustmentItemsPageResponse(
                itemsPage.page(),
                itemsPage.size(),
                itemsPage.totalItems(),
                itemsPage.totalPages(),
                includeCost,
                itemsPage.items()
        );
    }

    @org.springframework.transaction.annotation.Transactional
    public PriceAdjustmentBatchResponse deleteItem(String actorName, int companyId, int branchId, long batchId, long itemId) {
        securityService.requireAdjustmentCreate(actorName, companyId, branchId);
        
        PriceAdjustmentBatchResponse batch = repository.lockBatch(companyId, batchId);
        if (batch.branchId() != branchId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRICING_ADJUSTMENT_BATCH_NOT_FOUND",
                    "Adjustment batch was not found for this branch");
        }
        
        if (!List.of("DRAFT", "PREVIEWED").contains(batch.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICING_ADJUSTMENT_STATUS_INVALID",
                    "Cannot delete items from adjustment batch while status is " + batch.status());
        }
        
        int deleted = repository.deleteItem(companyId, branchId, batchId, itemId);
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRICING_ADJUSTMENT_ITEM_NOT_FOUND",
                    "Adjustment item was not found in this batch");
        }
        
        repository.recalculateAndUpdateCounts(companyId, batchId);
        
        auditService.log(companyId, branchId, "ADJUSTMENT_BATCH_ITEM_DELETED", "BATCH", String.valueOf(batchId),
                actorName, "Deleted item " + itemId + " from price adjustment batch",
                "{\"batchId\":" + batchId + ",\"itemId\":" + itemId + "}");
                
        return repository.findBatch(companyId, batchId);
    }

    PriceAdjustmentBatchRepository.AdjustmentItemDraft buildDraft(PriceAdjustmentBatchRepository.AdjustmentProductRow product,
                                                                  DynamicPricingPolicy policy,
                                                                  PriceAdjustmentMode mode,
                                                                  PriceAdjustmentDirection direction,
                                                                  BigDecimal adjustmentValue,
                                                                  PriceTarget target,
                                                                  boolean recommendedSource) {
        BigDecimal oldRetail = money(product.retailPrice());
        BigDecimal oldLowest = money(product.lowestPrice());
        BigDecimal buying = money(product.buyingPrice());
        BigDecimal newRetail = oldRetail;
        BigDecimal newLowest = oldLowest;

        if (recommendedSource) {
            newRetail = money(product.suggestedRetailPrice());
            newLowest = money(product.suggestedLowestPrice());
        } else {
            if (target == PriceTarget.RETAIL || target == PriceTarget.RETAIL_AND_LOWEST) {
                newRetail = adjust(oldRetail, mode, direction, adjustmentValue);
            }
            if (target == PriceTarget.LOWEST || target == PriceTarget.RETAIL_AND_LOWEST) {
                newLowest = adjust(oldLowest, mode, direction, adjustmentValue);
            }
        }

        newRetail = roundMoney(newRetail);
        newLowest = roundMoney(newLowest);

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        reasons.add(recommendedSource ? "RECOMMENDATION_PRICE" : mode.name());
        validateGuardrails(oldRetail, oldLowest, newRetail, newLowest, buying, policy, warnings, blocked);

        String status = blocked.isEmpty()
                ? (warnings.isEmpty() ? "VALID" : "WARNING")
                : "BLOCKED";
        BigDecimal deltaAmount = newRetail.subtract(oldRetail).setScale(4, RoundingMode.HALF_UP);
        BigDecimal deltaPct = oldRetail.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : deltaAmount.divide(oldRetail, 4, RoundingMode.HALF_UP);
        BigDecimal expectedMargin = newRetail.compareTo(BigDecimal.ZERO) <= 0
                ? null
                : newRetail.subtract(buying).divide(newRetail, 4, RoundingMode.HALF_UP);

        return new PriceAdjustmentBatchRepository.AdjustmentItemDraft(
                product.productId(),
                product.recommendationItemId(),
                product.productName(),
                oldRetail,
                newRetail,
                oldLowest,
                newLowest,
                buying,
                deltaAmount,
                deltaPct,
                expectedMargin,
                status,
                reasons,
                warnings,
                blocked
        );
    }

    private PriceAdjustmentPreviewResponse createManualBatch(String actorName, PriceAdjustmentPreviewRequest request,
                                                             String auditEvent) {
        PriceAdjustmentMode mode = parseMode(request.adjustmentMode());
        if (mode == PriceAdjustmentMode.RECOMMENDED_PRICE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_MODE_INVALID",
                    "Manual bulk adjustment must use PERCENTAGE or FIXED_AMOUNT");
        }
        PriceAdjustmentDirection direction = parseDirection(request.direction());
        PriceTarget target = parseTarget(request.priceTarget());
        int maxProducts = request.maxProducts() == null ? DEFAULT_MAX_PRODUCTS : request.maxProducts();
        List<PriceAdjustmentBatchRepository.AdjustmentProductRow> products = repository.findProductRows(
                new PriceAdjustmentBatchRepository.ProductScopeQuery(
                        request.companyId(),
                        request.branchId(),
                        request.query(),
                        request.productIds(),
                        request.category(),
                        request.major(),
                        request.businessLineKey(),
                        request.templateKey(),
                        request.supplierId(),
                        maxProducts
                )
        );

        long batchId = repository.createBatch(
                request.companyId(),
                request.branchId(),
                "BULK_MANUAL",
                null,
                "PREVIEWED",
                mode.name(),
                direction.name(),
                request.adjustmentValue(),
                priceTargetJson(target),
                scopeJson(request, maxProducts),
                actorName,
                request.reason()
        );
        Counts counts = insertDrafts(request.companyId(), request.branchId(), batchId, products, mode, direction, target, false,
                request.adjustmentValue());
        repository.updateCounts(request.companyId(), batchId, counts.total(), counts.valid(), counts.warning(), counts.blocked());
        auditService.log(request.companyId(), request.branchId(), auditEvent, "BATCH", String.valueOf(batchId),
                actorName, "Created bulk price adjustment preview", "{\"batchId\":" + batchId + "}");
        return response(actorName, request.companyId(), request.branchId(), batchId);
    }

    private Counts insertDrafts(int companyId, int branchId, long batchId,
                                List<PriceAdjustmentBatchRepository.AdjustmentProductRow> products,
                                PriceAdjustmentMode mode, PriceAdjustmentDirection direction,
                                PriceTarget target, boolean recommendedSource) {
        return insertDrafts(companyId, branchId, batchId, products, mode, direction, target, recommendedSource, null);
    }

    private Counts insertDrafts(int companyId, int branchId, long batchId,
                                List<PriceAdjustmentBatchRepository.AdjustmentProductRow> products,
                                PriceAdjustmentMode mode, PriceAdjustmentDirection direction,
                                PriceTarget target, boolean recommendedSource, BigDecimal adjustmentValue) {
        int valid = 0;
        int warning = 0;
        int blocked = 0;
        for (var product : products) {
            DynamicPricingPolicy policy = policyRepository.findEffectivePolicy(companyId, branchId, product.productId())
                    .orElseGet(() -> policyRepository.systemDefaultPolicy(companyId, branchId));
            var draft = buildDraft(product, policy, mode, direction, adjustmentValue, target, recommendedSource);
            repository.insertItem(companyId, branchId, batchId, draft);
            if ("BLOCKED".equals(draft.status())) {
                blocked++;
            } else if ("WARNING".equals(draft.status())) {
                warning++;
            } else {
                valid++;
            }
        }
        return new Counts(products.size(), valid, warning, blocked);
    }

    private PriceAdjustmentPreviewResponse response(String actorName, int companyId, int branchId, long batchId) {
        boolean includeCost = securityService.canReadCost(actorName, companyId, branchId);
        PriceAdjustmentBatchResponse batch = repository.findBatch(companyId, batchId);
        PriceAdjustmentBatchRepository.ItemsPage page = repository.findItems(companyId, branchId, batchId, null, 0, 100, includeCost);
        return new PriceAdjustmentPreviewResponse(batch, includeCost, page.items());
    }

    private void validateGuardrails(BigDecimal oldRetail, BigDecimal oldLowest, BigDecimal newRetail,
                                    BigDecimal newLowest, BigDecimal buying, DynamicPricingPolicy policy,
                                    List<String> warnings, List<String> blocked) {
        if (oldRetail.compareTo(BigDecimal.ZERO) <= 0 || buying.compareTo(BigDecimal.ZERO) <= 0) {
            blocked.add("INSUFFICIENT_DATA");
        }
        if (!policy.allowBelowCost()) {
            if (newRetail.compareTo(buying) < 0 || newLowest.compareTo(buying) < 0) {
                blocked.add("BELOW_COST_BLOCKED");
            }
        }
        if (newRetail.compareTo(newLowest) < 0) {
            blocked.add("PRICE_ORDER_BLOCKED");
        }
        BigDecimal retailDeltaPct = oldRetail.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : newRetail.subtract(oldRetail).divide(oldRetail, 4, RoundingMode.HALF_UP);
        if (retailDeltaPct.compareTo(policy.maxIncreasePct()) > 0) {
            blocked.add("MAX_INCREASE_BLOCKED");
        }
        if (retailDeltaPct.abs().compareTo(policy.maxDecreasePct()) > 0 && retailDeltaPct.compareTo(BigDecimal.ZERO) < 0) {
            blocked.add("MAX_DECREASE_BLOCKED");
        }
        if (policy.maxIncreaseAmount() != null && newRetail.subtract(oldRetail).compareTo(policy.maxIncreaseAmount()) > 0) {
            blocked.add("MAX_INCREASE_AMOUNT_BLOCKED");
        }
        if (policy.maxDecreaseAmount() != null && oldRetail.subtract(newRetail).compareTo(policy.maxDecreaseAmount()) > 0) {
            blocked.add("MAX_DECREASE_AMOUNT_BLOCKED");
        }
        if (policy.minFinalPrice() != null && (newRetail.compareTo(policy.minFinalPrice()) < 0 || newLowest.compareTo(policy.minFinalPrice()) < 0)) {
            blocked.add("MIN_FINAL_PRICE_BLOCKED");
        }
        if (policy.maxFinalPrice() != null && (newRetail.compareTo(policy.maxFinalPrice()) > 0 || newLowest.compareTo(policy.maxFinalPrice()) > 0)) {
            blocked.add("MAX_FINAL_PRICE_BLOCKED");
        }
        if (policy.approvalRequired()) {
            warnings.add("APPROVAL_REQUIRED");
        }
    }

    private BigDecimal adjust(BigDecimal current, PriceAdjustmentMode mode, PriceAdjustmentDirection direction,
                              BigDecimal adjustmentValue) {
        BigDecimal value = money(adjustmentValue);
        if (mode == PriceAdjustmentMode.PERCENTAGE) {
            BigDecimal pct = value.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
            return direction == PriceAdjustmentDirection.INCREASE
                    ? current.multiply(BigDecimal.ONE.add(pct))
                    : current.multiply(BigDecimal.ONE.subtract(pct));
        }
        return direction == PriceAdjustmentDirection.INCREASE
                ? current.add(value)
                : current.subtract(value);
    }

    private PriceAdjustmentMode parseMode(String mode) {
        try {
            return PriceAdjustmentMode.valueOf(mode.trim().toUpperCase());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_MODE_INVALID",
                    "Adjustment mode must be PERCENTAGE or FIXED_AMOUNT");
        }
    }

    private PriceAdjustmentDirection parseDirection(String direction) {
        try {
            return PriceAdjustmentDirection.valueOf(direction.trim().toUpperCase());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_DIRECTION_INVALID",
                    "Adjustment direction must be INCREASE or DECREASE");
        }
    }

    private PriceTarget parseTarget(String target) {
        if (target == null || target.isBlank()) {
            return PriceTarget.RETAIL;
        }
        try {
            return PriceTarget.valueOf(target.trim().toUpperCase());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_TARGET_INVALID",
                    "Price target must be RETAIL, LOWEST, or RETAIL_AND_LOWEST");
        }
    }

    private String normalizeItemStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of("VALID", "WARNING", "BLOCKED", "APPLIED", "FAILED", "SKIPPED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_ITEM_STATUS_INVALID",
                    "Adjustment item status filter is invalid");
        }
        return normalized;
    }

    private String normalizeBatchStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of("DRAFT", "PREVIEWED", "PENDING_APPROVAL", "APPROVED", "REJECTED",
                "APPLYING", "APPLIED", "PARTIALLY_APPLIED", "FAILED", "CANCELLED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_BATCH_STATUS_INVALID",
                    "Adjustment batch status filter is invalid");
        }
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundMoney(BigDecimal value) {
        return money(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String priceTargetJson(PriceTarget target) {
        if (target == PriceTarget.RETAIL_AND_LOWEST) {
            return "[\"RETAIL\",\"LOWEST\"]";
        }
        return "[\"" + target.name() + "\"]";
    }

    private String scopeJson(PriceAdjustmentPreviewRequest request, int maxProducts) {
        return String.format(Locale.ROOT, """
                {"query":%s,"productIdsCount":%d,"category":%s,"major":%s,"businessLineKey":%s,"templateKey":%s,"supplierId":%s,"maxProducts":%d}
                """,
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
        return value == null || value.isBlank() ? "null" : "\"" + value.trim().replace("\"", "\\\"") + "\"";
    }

    private record Counts(int total, int valid, int warning, int blocked) {
    }
}
