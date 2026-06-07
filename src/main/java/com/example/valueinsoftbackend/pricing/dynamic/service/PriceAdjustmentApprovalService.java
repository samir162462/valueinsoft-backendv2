package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApprovalRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentRejectRequest;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceAdjustmentBatchRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PriceAdjustmentApprovalService {

    private final PriceAdjustmentBatchRepository repository;
    private final DynamicPricingSecurityService securityService;
    private final PricingAuditService auditService;

    public PriceAdjustmentApprovalService(PriceAdjustmentBatchRepository repository,
                                          DynamicPricingSecurityService securityService,
                                          PricingAuditService auditService) {
        this.repository = repository;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    @Transactional
    public PriceAdjustmentBatchResponse submit(String actorName, int companyId, int branchId, long batchId) {
        securityService.requireAdjustmentSubmit(actorName, companyId, branchId);
        PriceAdjustmentBatchResponse batch = lockAndValidateBranch(companyId, branchId, batchId);
        requireStatus(batch, List.of("DRAFT", "PREVIEWED"), "submit");
        if (batch.totalItems() <= 0 || batch.validItems() + batch.warningItems() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_BATCH_EMPTY",
                    "Adjustment batch has no applicable rows to submit");
        }
        if (batch.blockedItems() > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICING_ADJUSTMENT_BATCH_HAS_BLOCKED_ITEMS",
                    "Resolve or remove blocked rows before submission");
        }
        repository.markSubmitted(companyId, batchId, actorName);
        auditService.log(companyId, branchId, "ADJUSTMENT_BATCH_SUBMITTED", "BATCH", String.valueOf(batchId),
                actorName, "Submitted price adjustment batch for approval", "{\"batchId\":" + batchId + "}");
        return repository.findBatch(companyId, batchId);
    }

    @Transactional
    public PriceAdjustmentBatchResponse approve(String actorName, int companyId, int branchId, long batchId,
                                                PriceAdjustmentApprovalRequest request) {
        securityService.requireAdjustmentApprove(actorName, companyId, branchId);
        PriceAdjustmentBatchResponse batch = lockAndValidateBranch(companyId, branchId, batchId);
        requireStatus(batch, List.of("PENDING_APPROVAL"), "approve");
        if (actorName != null && actorName.equalsIgnoreCase(batch.createdBy())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICING_ADJUSTMENT_MAKER_CHECKER_REQUIRED",
                    "The creator cannot approve this adjustment batch");
        }
        repository.markApproved(companyId, batchId, actorName);
        auditService.log(companyId, branchId, "ADJUSTMENT_BATCH_APPROVED", "BATCH", String.valueOf(batchId),
                actorName, "Approved price adjustment batch", notePayload(batchId, request == null ? null : request.note()));
        return repository.findBatch(companyId, batchId);
    }

    @Transactional
    public PriceAdjustmentBatchResponse reject(String actorName, int companyId, int branchId, long batchId,
                                               PriceAdjustmentRejectRequest request) {
        securityService.requireAdjustmentApprove(actorName, companyId, branchId);
        PriceAdjustmentBatchResponse batch = lockAndValidateBranch(companyId, branchId, batchId);
        requireStatus(batch, List.of("PENDING_APPROVAL"), "reject");
        repository.markRejected(companyId, batchId, actorName);
        auditService.log(companyId, branchId, "ADJUSTMENT_BATCH_REJECTED", "BATCH", String.valueOf(batchId),
                actorName, "Rejected price adjustment batch", notePayload(batchId, request == null ? null : request.reason()));
        return repository.findBatch(companyId, batchId);
    }

    @Transactional
    public PriceAdjustmentBatchResponse cancel(String actorName, int companyId, int branchId, long batchId) {
        securityService.requireAdjustmentSubmit(actorName, companyId, branchId);
        PriceAdjustmentBatchResponse batch = lockAndValidateBranch(companyId, branchId, batchId);
        requireStatus(batch, List.of("DRAFT", "PREVIEWED", "PENDING_APPROVAL"), "cancel");
        repository.markCancelled(companyId, batchId);
        auditService.log(companyId, branchId, "ADJUSTMENT_BATCH_CANCELLED", "BATCH", String.valueOf(batchId),
                actorName, "Cancelled price adjustment batch", "{\"batchId\":" + batchId + "}");
        return repository.findBatch(companyId, batchId);
    }

    private PriceAdjustmentBatchResponse lockAndValidateBranch(int companyId, int branchId, long batchId) {
        PriceAdjustmentBatchResponse batch = repository.lockBatch(companyId, batchId);
        if (batch.branchId() != branchId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRICING_ADJUSTMENT_BATCH_NOT_FOUND",
                    "Adjustment batch was not found for this branch");
        }
        return batch;
    }

    private void requireStatus(PriceAdjustmentBatchResponse batch, List<String> allowedStatuses, String action) {
        if (!allowedStatuses.contains(batch.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICING_ADJUSTMENT_STATUS_INVALID",
                    "Cannot " + action + " adjustment batch while status is " + batch.status());
        }
    }

    private String notePayload(long batchId, String note) {
        return "{\"batchId\":" + batchId + ",\"note\":" + jsonString(note) + "}";
    }

    private static String jsonString(String value) {
        return value == null || value.isBlank() ? "null" : "\"" + value.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
