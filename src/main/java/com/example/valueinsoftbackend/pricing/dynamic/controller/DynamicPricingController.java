package com.example.valueinsoftbackend.pricing.dynamic.controller;

import com.example.valueinsoftbackend.pricing.dynamic.dto.DynamicPricingPolicyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.DynamicPricingPolicyResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.EffectivePricingPolicyResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchesPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApplyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApplyResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApprovalRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentCreateRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentItemsPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentPreviewRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentPreviewResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentRejectRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationBulkRoundRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationItemResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationItemUpdateRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationItemsPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationRunRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationRunResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PricingMetricsRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PricingMetricsResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.RecommendationAdjustmentCreateRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.InflationPreviewRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.InflationPreviewResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.InflationApplyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.InflationApplyResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingCostUpdateRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingProductRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingProductResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingProductsPageResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.UsdPricingRateResponse;
import com.example.valueinsoftbackend.pricing.dynamic.service.BulkPriceAdjustmentService;
import com.example.valueinsoftbackend.pricing.dynamic.service.DynamicPricingPolicyService;
import com.example.valueinsoftbackend.pricing.dynamic.service.PriceAdjustmentApplyService;
import com.example.valueinsoftbackend.pricing.dynamic.service.PriceAdjustmentApprovalService;
import com.example.valueinsoftbackend.pricing.dynamic.service.PriceRecommendationService;
import com.example.valueinsoftbackend.pricing.dynamic.service.PricingMetricsService;
import com.example.valueinsoftbackend.pricing.dynamic.service.InflationPricingService;
import com.example.valueinsoftbackend.pricing.dynamic.service.UsdPricingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/inventory/pricing")
public class DynamicPricingController {

    private final DynamicPricingPolicyService policyService;
    private final PricingMetricsService metricsService;
    private final PriceRecommendationService recommendationService;
    private final BulkPriceAdjustmentService adjustmentService;
    private final PriceAdjustmentApprovalService approvalService;
    private final PriceAdjustmentApplyService applyService;
    private final InflationPricingService inflationPricingService;
    private final UsdPricingService usdPricingService;

    public DynamicPricingController(DynamicPricingPolicyService policyService,
                                    PricingMetricsService metricsService,
                                    PriceRecommendationService recommendationService,
                                    BulkPriceAdjustmentService adjustmentService,
                                    PriceAdjustmentApprovalService approvalService,
                                    PriceAdjustmentApplyService applyService,
                                    InflationPricingService inflationPricingService,
                                    UsdPricingService usdPricingService) {
        this.policyService = policyService;
        this.metricsService = metricsService;
        this.recommendationService = recommendationService;
        this.adjustmentService = adjustmentService;
        this.approvalService = approvalService;
        this.applyService = applyService;
        this.inflationPricingService = inflationPricingService;
        this.usdPricingService = usdPricingService;
    }

    @GetMapping("/policy/effective")
    public EffectivePricingPolicyResponse effectivePolicy(Principal principal,
                                                          @RequestParam @Positive Integer companyId,
                                                          @RequestParam @Positive Integer branchId,
                                                          @RequestParam(required = false) @Positive Long productId) {
        return policyService.effectivePolicy(principal.getName(), companyId, branchId, productId);
    }

    @PostMapping("/policies")
    public DynamicPricingPolicyResponse savePolicy(Principal principal,
                                                   @Valid @RequestBody DynamicPricingPolicyRequest request) {
        return policyService.savePolicy(principal.getName(), request);
    }

    @GetMapping("/policies")
    public java.util.List<DynamicPricingPolicyResponse> listPolicies(Principal principal,
                                                                     @RequestParam @Positive Integer companyId,
                                                                     @RequestParam(required = false) @Positive Integer branchId) {
        return policyService.listPolicies(principal.getName(), companyId, branchId);
    }

    @GetMapping("/policies/{policyId}")
    public DynamicPricingPolicyResponse getPolicy(Principal principal,
                                                  @RequestParam @Positive Integer companyId,
                                                  @PathVariable @Positive Long policyId) {
        return policyService.getPolicy(principal.getName(), companyId, policyId);
    }

    @DeleteMapping("/policies/{policyId}")
    public void deletePolicy(Principal principal,
                             @RequestParam @Positive Integer companyId,
                             @PathVariable @Positive Long policyId) {
        policyService.deletePolicy(principal.getName(), companyId, policyId);
    }

    @PostMapping("/metrics/preview")
    public PricingMetricsResponse previewMetrics(Principal principal,
                                                 @Valid @RequestBody PricingMetricsRequest request) {
        return metricsService.previewMetrics(principal.getName(), request);
    }

    @PostMapping("/recommendations/runs")
    public PriceRecommendationRunResponse createRecommendationRun(Principal principal,
                                                                  @Valid @RequestBody PriceRecommendationRunRequest request) {
        return recommendationService.createRun(principal.getName(), request);
    }

    @GetMapping("/recommendations/runs/{runId}")
    public PriceRecommendationRunResponse getRecommendationRun(Principal principal,
                                                               @PathVariable @Positive Long runId,
                                                               @RequestParam @Positive Integer companyId,
                                                               @RequestParam @Positive Integer branchId) {
        return recommendationService.getRun(principal.getName(), companyId, branchId, runId);
    }

    @GetMapping("/recommendations/runs/{runId}/items")
    public PriceRecommendationItemsPageResponse getRecommendationItems(Principal principal,
                                                                       @PathVariable @Positive Long runId,
                                                                       @RequestParam @Positive Integer companyId,
                                                                       @RequestParam @Positive Integer branchId,
                                                                       @RequestParam(required = false) String status,
                                                                       @RequestParam(defaultValue = "0") Integer page,
                                                                       @RequestParam(defaultValue = "50") Integer size) {
        return recommendationService.getItems(principal.getName(), companyId, branchId, runId, status, page, size);
    }

    @PostMapping("/recommendations/items/{itemId}/price")
    public PriceRecommendationItemResponse updateRecommendationItemPrice(Principal principal,
                                                                         @PathVariable @Positive Long itemId,
                                                                         @Valid @RequestBody PriceRecommendationItemUpdateRequest request) {
        return recommendationService.updateItemPrice(principal.getName(), request.companyId(), request.branchId(), itemId, request.suggestedRetailPrice());
    }

    @PostMapping("/recommendations/runs/{runId}/round")
    public void bulkRoundRecommendationPrices(Principal principal,
                                             @PathVariable @Positive Long runId,
                                             @Valid @RequestBody PriceRecommendationBulkRoundRequest request) {
        recommendationService.bulkRoundPrices(principal.getName(), request.companyId(), request.branchId(), runId, request.roundingFactor());
    }

    @PostMapping("/adjustments/preview")
    public PriceAdjustmentPreviewResponse previewAdjustment(Principal principal,
                                                           @Valid @RequestBody PriceAdjustmentPreviewRequest request) {
        return adjustmentService.preview(principal.getName(), request);
    }

    @PostMapping("/adjustments")
    public PriceAdjustmentPreviewResponse createAdjustment(Principal principal,
                                                          @Valid @RequestBody PriceAdjustmentCreateRequest request) {
        return adjustmentService.create(principal.getName(), request);
    }

    @GetMapping("/adjustments/{batchId}")
    public PriceAdjustmentBatchResponse getAdjustmentBatch(Principal principal,
                                                           @PathVariable @Positive Long batchId,
                                                           @RequestParam @Positive Integer companyId,
                                                           @RequestParam @Positive Integer branchId) {
        return adjustmentService.getBatch(principal.getName(), companyId, branchId, batchId);
    }

    @GetMapping("/adjustments")
    public PriceAdjustmentBatchesPageResponse getAdjustmentBatches(Principal principal,
                                                                   @RequestParam @Positive Integer companyId,
                                                                   @RequestParam @Positive Integer branchId,
                                                                   @RequestParam(required = false) String status,
                                                                   @RequestParam(defaultValue = "0") Integer page,
                                                                   @RequestParam(defaultValue = "50") Integer size) {
        return adjustmentService.getBatches(principal.getName(), companyId, branchId, status, page, size);
    }

    @GetMapping("/adjustments/{batchId}/items")
    public PriceAdjustmentItemsPageResponse getAdjustmentItems(Principal principal,
                                                               @PathVariable @Positive Long batchId,
                                                               @RequestParam @Positive Integer companyId,
                                                               @RequestParam @Positive Integer branchId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "0") Integer page,
                                                               @RequestParam(defaultValue = "50") Integer size) {
        return adjustmentService.getItems(principal.getName(), companyId, branchId, batchId, status, page, size);
    }

    @PostMapping("/recommendations/runs/{runId}/create-adjustment")
    public PriceAdjustmentPreviewResponse createAdjustmentFromRecommendation(Principal principal,
                                                                            @PathVariable @Positive Long runId,
                                                                            @Valid @RequestBody RecommendationAdjustmentCreateRequest request) {
        return adjustmentService.createFromRecommendation(principal.getName(), runId, request);
    }

    @PostMapping("/adjustments/{batchId}/submit")
    public PriceAdjustmentBatchResponse submitAdjustment(Principal principal,
                                                         @PathVariable @Positive Long batchId,
                                                         @RequestParam @Positive Integer companyId,
                                                         @RequestParam @Positive Integer branchId) {
        return approvalService.submit(principal.getName(), companyId, branchId, batchId);
    }

    @PostMapping("/adjustments/{batchId}/approve")
    public PriceAdjustmentBatchResponse approveAdjustment(Principal principal,
                                                          @PathVariable @Positive Long batchId,
                                                          @RequestParam @Positive Integer companyId,
                                                          @RequestParam @Positive Integer branchId,
                                                          @RequestBody(required = false) PriceAdjustmentApprovalRequest request) {
        return approvalService.approve(principal.getName(), companyId, branchId, batchId, request);
    }

    @PostMapping("/adjustments/{batchId}/reject")
    public PriceAdjustmentBatchResponse rejectAdjustment(Principal principal,
                                                         @PathVariable @Positive Long batchId,
                                                         @RequestParam @Positive Integer companyId,
                                                         @RequestParam @Positive Integer branchId,
                                                         @Valid @RequestBody PriceAdjustmentRejectRequest request) {
        return approvalService.reject(principal.getName(), companyId, branchId, batchId, request);
    }

    @PostMapping("/adjustments/{batchId}/cancel")
    public PriceAdjustmentBatchResponse cancelAdjustment(Principal principal,
                                                         @PathVariable @Positive Long batchId,
                                                         @RequestParam @Positive Integer companyId,
                                                         @RequestParam @Positive Integer branchId) {
        return approvalService.cancel(principal.getName(), companyId, branchId, batchId);
    }

    @DeleteMapping("/adjustments/{batchId}/items/{itemId}")
    public PriceAdjustmentBatchResponse deleteAdjustmentItem(Principal principal,
                                                             @PathVariable @Positive Long batchId,
                                                             @PathVariable @Positive Long itemId,
                                                             @RequestParam @Positive Integer companyId,
                                                             @RequestParam @Positive Integer branchId) {
        return adjustmentService.deleteItem(principal.getName(), companyId, branchId, batchId, itemId);
    }

    @PostMapping("/adjustments/{batchId}/apply")
    public PriceAdjustmentApplyResponse applyAdjustment(Principal principal,
                                                        @PathVariable @Positive Long batchId,
                                                        @RequestParam @Positive Integer companyId,
                                                        @RequestParam @Positive Integer branchId,
                                                        @RequestBody(required = false) PriceAdjustmentApplyRequest request) {
        return applyService.apply(principal.getName(), companyId, branchId, batchId, request);
    }

    @PostMapping("/inflation/preview")
    public InflationPreviewResponse previewInflation(Principal principal,
                                                     @Valid @RequestBody InflationPreviewRequest request) {
        return inflationPricingService.preview(principal.getName(), request);
    }

    @PostMapping("/inflation/apply")
    public InflationApplyResponse applyInflation(Principal principal,
                                                 @Valid @RequestBody InflationApplyRequest request) {
        return inflationPricingService.apply(principal.getName(), request);
    }

    @PostMapping("/usd/products")
    public UsdPricingProductsPageResponse listUsdPricingProducts(Principal principal,
                                                                 @Valid @RequestBody UsdPricingProductRequest request) {
        return usdPricingService.listProducts(principal.getName(), request);
    }

    @GetMapping("/usd/exchange-rate/current")
    public UsdPricingRateResponse getCurrentUsdPricingRate(Principal principal,
                                                           @RequestParam @Positive Integer companyId,
                                                           @RequestParam @Positive Integer branchId) {
        return usdPricingService.currentRate(principal.getName(), companyId, branchId);
    }

    @PutMapping("/usd/products/{productId}/cost")
    public UsdPricingProductResponse updateUsdReplacementCost(Principal principal,
                                                              @PathVariable @Positive Long productId,
                                                              @Valid @RequestBody UsdPricingCostUpdateRequest request) {
        return usdPricingService.updateCost(principal.getName(), productId, request);
    }
}
