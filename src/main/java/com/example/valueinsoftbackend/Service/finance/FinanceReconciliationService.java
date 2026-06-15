package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceReconciliation;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationItemItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationRunItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceImportResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationItemResolutionRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationRunCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportItemRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FinanceReconciliationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final Set<String> RECONCILIATION_TYPES = Set.of(
            "cash_drawer",
            "card_settlement",
            "bank",
            "inventory_valuation",
            "supplier",
            "customer");
    private static final Set<String> RUN_STATUSES = Set.of(
            "pending",
            "running",
            "completed",
            "completed_with_exceptions",
            "failed",
            "cancelled");
    private static final Set<String> MATCH_STATUSES = Set.of(
            "matched",
            "unmatched_source",
            "unmatched_ledger",
            "difference",
            "ignored");
    private static final Set<String> RESOLUTION_STATUSES = Set.of(
            "unresolved",
            "proposed",
            "resolved",
            "dismissed");

    private final DbFinanceReconciliation dbFinanceReconciliation;
    private final DbFinanceSetup dbFinanceSetup;
    private final AuthorizationService authorizationService;
    private final FinanceAuditService financeAuditService;
    private final FinanceOperationalPostingService financeOperationalPostingService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public FinanceReconciliationService(DbFinanceReconciliation dbFinanceReconciliation,
                                        DbFinanceSetup dbFinanceSetup,
                                        AuthorizationService authorizationService,
                                        FinanceAuditService financeAuditService,
                                        FinanceOperationalPostingService financeOperationalPostingService,
                                        StorageService storageService,
                                        ObjectMapper objectMapper) {
        this.dbFinanceReconciliation = dbFinanceReconciliation;
        this.dbFinanceSetup = dbFinanceSetup;
        this.authorizationService = authorizationService;
        this.financeAuditService = financeAuditService;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FinanceReconciliationRunItem createRunForAuthenticatedUser(String authenticatedName,
                                                                      FinanceReconciliationRunCreateRequest request) {
        validateCreateRequest(request);
        authorizeEdit(authenticatedName, request.getCompanyId(), request.getBranchId());

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        FinanceReconciliationRunItem run = dbFinanceReconciliation.createRun(request, actorUserId);
        List<String> mappingKeys = mappingKeysForType(request.getReconciliationType());
        dbFinanceReconciliation.createMatchedSourceItems(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                mappingKeys,
                actorUserId);
        dbFinanceReconciliation.createUnmatchedSourceItems(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                actorUserId);
        dbFinanceReconciliation.refreshSourceItemStatusesForRun(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                actorUserId);
        dbFinanceReconciliation.createUnmatchedLedgerItems(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                request.getBranchId(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                mappingKeys,
                actorUserId);
        FinanceReconciliationRunItem completed = dbFinanceReconciliation.completeRun(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                actorUserId);

        financeAuditService.recordEvent(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                "finance.reconciliation.run.completed",
                "finance_reconciliation_run",
                completed.getReconciliationRunId().toString(),
                Map.of(
                        "reconciliationType", completed.getReconciliationType(),
                        "periodStart", completed.getPeriodStart().toString(),
                        "periodEnd", completed.getPeriodEnd().toString(),
                        "status", completed.getStatus(),
                        "differenceAmount", completed.getDifferenceAmount()),
                "Finance reconciliation run completed");
        return completed;
    }

    @Transactional
    public FinanceReconciliationSourceImportResponse importSourceItemsForAuthenticatedUser(String authenticatedName,
                                                                                          FinanceReconciliationSourceImportRequest request) {
        validateSourceImportRequest(request);
        authorizeEdit(authenticatedName, request.getCompanyId(), request.getBranchId());

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        ArrayList<String> rawPayloadJson = new ArrayList<>();
        for (FinanceReconciliationSourceImportItemRequest item : request.getItems()) {
            rawPayloadJson.add(toJson(item.getRawPayload()));
        }
        ArrayList<FinanceReconciliationSourceItem> imported = dbFinanceReconciliation.importSourceItems(
                request.getCompanyId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getSourceSystem(),
                request.getItems(),
                rawPayloadJson,
                actorUserId);

        enqueueImportedSettlementPostingRequests(request, imported);

        FinanceReconciliationSourceImportResponse response = new FinanceReconciliationSourceImportResponse(
                request.getCompanyId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getSourceSystem(),
                imported.size(),
                java.time.Instant.now(),
                imported);
        financeAuditService.recordEvent(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                "finance.reconciliation.source_items.imported",
                "finance_reconciliation_source_item",
                request.getSourceSystem(),
                Map.of(
                        "reconciliationType", request.getReconciliationType(),
                        "sourceSystem", request.getSourceSystem(),
                        "importedCount", imported.size()),
                "Finance reconciliation source items imported");
        return response;
    }

    private void enqueueImportedSettlementPostingRequests(FinanceReconciliationSourceImportRequest request,
                                                          List<FinanceReconciliationSourceItem> imported) {
        if (!"card_settlement".equals(request.getReconciliationType()) || imported == null || imported.isEmpty()) {
            return;
        }

        for (FinanceReconciliationSourceItem sourceItem : imported) {
            try {
                Map<String, Object> rawPayload = parseRawPayload(sourceItem.getRawPayloadJson());
                String sourceType = importedSettlementSourceType(rawPayload);
                BigDecimal grossAmount = firstPositiveAmount(rawPayload,
                        "grossAmount",
                        "settlementGrossAmount",
                        "clearingAmount",
                        "expectedAmount",
                        "amount");
                if (grossAmount == null) {
                    grossAmount = positiveOrNull(sourceItem.getAmount());
                }
                if (grossAmount == null) {
                    continue;
                }

                BigDecimal feeAmount = firstAmountOrZero(rawPayload,
                        "feeAmount",
                        "providerFeeAmount",
                        "processingFeeAmount");
                BigDecimal netAmount = firstPositiveAmount(rawPayload,
                        "netAmount",
                        "settledAmount",
                        "depositAmount",
                        "bankAmount",
                        "cashAmount");
                if (grossAmount == null && netAmount != null) {
                    grossAmount = netAmount.add(feeAmount).setScale(4, java.math.RoundingMode.HALF_UP);
                }
                if (netAmount == null) {
                    netAmount = grossAmount.subtract(feeAmount).setScale(4, java.math.RoundingMode.HALF_UP);
                }
                if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                String settlementMethod = importedSettlementMethod(rawPayload, sourceType);
                String destination = importedSettlementDestination(rawPayload, settlementMethod);
                String paymentId = firstText(rawPayload,
                        "paymentId",
                        "settlementId",
                        "providerSettlementId",
                        "providerReference",
                        "externalReference");
                if (paymentId == null) {
                    paymentId = sourceItem.getExternalReference();
                }

                LinkedHashMap<String, Object> extraPayload = new LinkedHashMap<>();
                extraPayload.put("sourceSystem", sourceItem.getSourceSystem());
                extraPayload.put("externalReference", sourceItem.getExternalReference());
                extraPayload.put("reconciliationSourceItemId", sourceItem.getReconciliationSourceItemId());
                extraPayload.put("description", sourceItem.getDescription());

                financeOperationalPostingService.enqueueImportedProviderSettlement(
                        request.getCompanyId(),
                        sourceItem.getBranchId(),
                        sourceType,
                        settlementPostingSourceId(sourceItem),
                        grossAmount,
                        feeAmount,
                        netAmount,
                        settlementMethod,
                        destination,
                        paymentId,
                        Timestamp.valueOf(sourceItem.getSourceDate().atStartOfDay()),
                        "system",
                        extraPayload);
            } catch (RuntimeException exception) {
                // Keep reconciliation import successful even when finance setup or payload normalization is incomplete.
            }
        }
    }

    public ArrayList<FinanceReconciliationSourceItem> getSourceItemsForAuthenticatedUser(String authenticatedName,
                                                                                        int companyId,
                                                                                        Integer branchId,
                                                                                        String reconciliationType,
                                                                                        String sourceSystem,
                                                                                        String status,
                                                                                        Integer limit,
                                                                                        Integer offset) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId, branchId);

        String normalizedType = normalizeOptional(reconciliationType);
        if (normalizedType != null && !RECONCILIATION_TYPES.contains(normalizedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        String normalizedSourceSystem = normalizeRequiredIdentifier(sourceSystem, "FINANCE_RECONCILIATION_SOURCE_SYSTEM_INVALID",
                false);
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null && !Set.of("imported", "matched", "exception", "ignored").contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_STATUS_INVALID",
                    "Invalid reconciliation source item status");
        }

        return dbFinanceReconciliation.getSourceItems(
                companyId,
                branchId,
                normalizedType,
                normalizedSourceSystem,
                normalizedStatus,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    public ArrayList<FinanceReconciliationRunItem> getRunsForAuthenticatedUser(String authenticatedName,
                                                                               int companyId,
                                                                               Integer branchId,
                                                                               String reconciliationType,
                                                                               String status,
                                                                               Integer limit,
                                                                               Integer offset) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId, branchId);

        String normalizedType = normalizeOptional(reconciliationType);
        if (normalizedType != null && !RECONCILIATION_TYPES.contains(normalizedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null && !RUN_STATUSES.contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_STATUS_INVALID",
                    "Invalid reconciliation run status");
        }

        return dbFinanceReconciliation.getRuns(
                companyId,
                branchId,
                normalizedType,
                normalizedStatus,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    public FinanceReconciliationRunItem getRunForAuthenticatedUser(String authenticatedName,
                                                                   int companyId,
                                                                   UUID reconciliationRunId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId, null);
        return requireRun(companyId, reconciliationRunId);
    }

    public ArrayList<FinanceReconciliationItemItem> getItemsForAuthenticatedUser(String authenticatedName,
                                                                                 int companyId,
                                                                                 UUID reconciliationRunId,
                                                                                 String matchStatus,
                                                                                 String resolutionStatus,
                                                                                 Integer limit,
                                                                                 Integer offset) {
        requireCompany(companyId);
        FinanceReconciliationRunItem run = requireRun(companyId, reconciliationRunId);
        authorizeRead(authenticatedName, companyId, run.getBranchId());

        String normalizedMatchStatus = normalizeOptional(matchStatus);
        if (normalizedMatchStatus != null && !MATCH_STATUSES.contains(normalizedMatchStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_MATCH_STATUS_INVALID",
                    "Invalid reconciliation match status");
        }
        String normalizedResolutionStatus = normalizeOptional(resolutionStatus);
        if (normalizedResolutionStatus != null && !RESOLUTION_STATUSES.contains(normalizedResolutionStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_RESOLUTION_STATUS_INVALID",
                    "Invalid reconciliation resolution status");
        }

        return dbFinanceReconciliation.getItems(
                companyId,
                reconciliationRunId,
                normalizedMatchStatus,
                normalizedResolutionStatus,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    @Transactional
    public FinanceReconciliationItemItem updateItemResolutionForAuthenticatedUser(String authenticatedName,
                                                                                  UUID reconciliationRunId,
                                                                                  UUID reconciliationItemId,
                                                                                  FinanceReconciliationItemResolutionRequest request) {
        requireCompany(request.getCompanyId());
        FinanceReconciliationItemItem item = requireItem(request.getCompanyId(), reconciliationItemId);
        if (!item.getReconciliationRunId().equals(reconciliationRunId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_ITEM_RUN_MISMATCH",
                    "Reconciliation item does not belong to the requested run");
        }
        FinanceReconciliationRunItem run = requireRun(request.getCompanyId(), item.getReconciliationRunId());
        authorizeEdit(authenticatedName, request.getCompanyId(), run.getBranchId());

        String resolutionStatus = normalizeOptional(request.getResolutionStatus());
        if (resolutionStatus == null || !RESOLUTION_STATUSES.contains(resolutionStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_RESOLUTION_STATUS_INVALID",
                    "Invalid reconciliation resolution status");
        }
        String note = request.getResolutionNote() == null ? null : request.getResolutionNote().trim();
        if (("resolved".equals(resolutionStatus) || "dismissed".equals(resolutionStatus))
                && (note == null || note.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_RESOLUTION_NOTE_REQUIRED",
                    "Resolved or dismissed reconciliation items require a resolution note");
        }

        if (request.getProofFileKey() != null) {
            // Robust check for existing key preservation (ignore case and whitespace)
            String existingKey = item.getResolutionProofFileKey();
            boolean isPreservingExisting = existingKey != null && 
                                           request.getProofFileKey().trim().equalsIgnoreCase(existingKey.trim());

            if (!isPreservingExisting) {
                String expectedPrefix = String.format(java.util.Locale.ROOT, "finance/reconciliation/%d/%s/%s/",
                        request.getCompanyId(),
                        reconciliationRunId.toString().toLowerCase(),
                        reconciliationItemId.toString().toLowerCase());
                
                String actualKey = request.getProofFileKey().toLowerCase().trim();
                if (!actualKey.startsWith(expectedPrefix.toLowerCase())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PROOF_KEY_INVALID",
                            "Proof file key does not match the reconciliation item context. Expected prefix: " + expectedPrefix);
                }
            }
        }

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        FinanceReconciliationItemItem updated = dbFinanceReconciliation.updateItemResolution(
                request.getCompanyId(),
                reconciliationItemId,
                resolutionStatus,
                note,
                request,
                actorUserId);
        dbFinanceReconciliation.refreshRunDifference(
                request.getCompanyId(),
                updated.getReconciliationRunId(),
                actorUserId);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("reconciliationRunId", updated.getReconciliationRunId().toString());
        auditPayload.put("resolutionStatus", updated.getResolutionStatus());
        auditPayload.put("matchStatus", updated.getMatchStatus());
        if (updated.getResolutionProofFileKey() != null) {
            auditPayload.put("proofFileKey", updated.getResolutionProofFileKey());
            auditPayload.put("proofFileName", updated.getResolutionProofFileName());
        }

        financeAuditService.recordEvent(
                authenticatedName,
                request.getCompanyId(),
                run.getBranchId(),
                "finance.reconciliation.item.resolution_updated",
                "finance_reconciliation_item",
                updated.getReconciliationItemId().toString(),
                auditPayload,
                "Finance reconciliation item resolution updated" + (updated.getResolutionProofFileKey() != null ? " with proof" : ""));
        return updated;
    }

    public com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationItemItem uploadProofRelay(
            String authenticatedName,
            UUID reconciliationRunId,
            UUID reconciliationItemId,
            int companyId,
            String resolutionStatus,
            String resolutionNote,
            org.springframework.web.multipart.MultipartFile file) {
        
        requireCompany(companyId);
        FinanceReconciliationItemItem item = requireItem(companyId, reconciliationItemId);
        if (!item.getReconciliationRunId().equals(reconciliationRunId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_ITEM_RUN_MISMATCH",
                    "Reconciliation item does not belong to the requested run");
        }
        FinanceReconciliationRunItem run = requireRun(companyId, item.getReconciliationRunId());
        authorizeEdit(authenticatedName, companyId, run.getBranchId());

        if (file.getSize() > 1024 * 1024) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PROOF_TOO_LARGE", "Max 1MB");
        }

        String contentType = file.getContentType();
        String extension = contentType.contains("pdf") ? "pdf" : "jpg";
        String fileKey = String.format(java.util.Locale.ROOT, "finance/reconciliation/%d/%s/%s/%s.%s",
                companyId, reconciliationRunId, reconciliationItemId, UUID.randomUUID(), extension);

        try {
            // Internal upload to S3
            storageService.uploadFile(fileKey, file.getBytes(), contentType);
            
            // Resolve actual user ID for the foreign key
            Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);

            // Update database
            return dbFinanceReconciliation.updateItemResolution(
                    companyId,
                    reconciliationItemId,
                    resolutionStatus,
                    resolutionNote,
                    new com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationItemResolutionRequest(
                            companyId,
                            resolutionStatus,
                            resolutionNote,
                            fileKey,
                            file.getOriginalFilename(),
                            contentType,
                            file.getSize()
                    ),
                    actorUserId); 
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Failed to relay upload to S3");
        }
    }

    public com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationProofUploadResponse prepareProofUploadForAuthenticatedUser(
            String authenticatedName,
            UUID reconciliationRunId,
            UUID reconciliationItemId,
            com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationProofUploadRequest request) {
        requireCompany(request.getCompanyId());
        FinanceReconciliationItemItem item = requireItem(request.getCompanyId(), reconciliationItemId);
        if (!item.getReconciliationRunId().equals(reconciliationRunId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_ITEM_RUN_MISMATCH",
                    "Reconciliation item does not belong to the requested run");
        }
        FinanceReconciliationRunItem run = requireRun(request.getCompanyId(), item.getReconciliationRunId());
        authorizeEdit(authenticatedName, request.getCompanyId(), run.getBranchId());

        // Validate metadata
        if (request.getFileSize() > 1024 * 1024) { // 1MB
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PROOF_TOO_LARGE",
                    "Proof file size cannot exceed 1MB");
        }
        String contentType = request.getContentType().toLowerCase();
        if (!Set.of("image/jpeg", "image/png", "image/webp", "application/pdf").contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PROOF_TYPE_INVALID",
                    "Only JPEG, PNG, WebP and PDF files are allowed as proofs");
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> "bin";
        };

        String fileKey = String.format(java.util.Locale.ROOT, "finance/reconciliation/%d/%s/%s/%s.%s",
                request.getCompanyId(),
                reconciliationRunId,
                reconciliationItemId,
                UUID.randomUUID(),
                extension);

        java.net.URL uploadUrl = storageService.generatePresignedUploadUrl(fileKey, contentType);
        
        return new com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationProofUploadResponse(
                fileKey,
                uploadUrl.toString());
    }

    public String generateProofDownloadUrlForAuthenticatedUser(String authenticatedName,
                                                               int companyId,
                                                               UUID reconciliationItemId) {
        requireCompany(companyId);
        FinanceReconciliationItemItem item = requireItem(companyId, reconciliationItemId);
        FinanceReconciliationRunItem run = requireRun(companyId, item.getReconciliationRunId());
        authorizeRead(authenticatedName, companyId, run.getBranchId());

        if (item.getResolutionProofFileKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_RECONCILIATION_PROOF_NOT_FOUND",
                    "No proof file attached to this reconciliation item");
        }

        return storageService.generatePresignedDownloadUrl(item.getResolutionProofFileKey()).toString();
    }

    private void validateCreateRequest(FinanceReconciliationRunCreateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_REQUEST_REQUIRED",
                    "Reconciliation run request is required");
        }
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        request.setReconciliationType(normalizeOptional(request.getReconciliationType()));
        if (request.getReconciliationType() == null || !RECONCILIATION_TYPES.contains(request.getReconciliationType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        if (request.getPeriodStart() == null || request.getPeriodEnd() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PERIOD_REQUIRED",
                    "Reconciliation period start and end are required");
        }
        if (request.getPeriodStart().isAfter(request.getPeriodEnd())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PERIOD_INVALID",
                    "Reconciliation period start cannot be after period end");
        }
        if (request.getPeriodEnd().isAfter(LocalDate.now().plusDays(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PERIOD_INVALID",
                    "Reconciliation period end is too far in the future");
        }
    }

    private void validateSourceImportRequest(FinanceReconciliationSourceImportRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_IMPORT_REQUIRED",
                    "Reconciliation source import request is required");
        }
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        request.setReconciliationType(normalizeOptional(request.getReconciliationType()));
        if (request.getReconciliationType() == null || !RECONCILIATION_TYPES.contains(request.getReconciliationType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        request.setSourceSystem(normalizeRequiredIdentifier(request.getSourceSystem(),
                "FINANCE_RECONCILIATION_SOURCE_SYSTEM_INVALID", true));
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_ITEMS_REQUIRED",
                    "At least one reconciliation source item is required");
        }
        if (request.getItems().size() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_ITEMS_TOO_MANY",
                    "A single import request cannot exceed 500 source items");
        }
        normalizeImportedSourceItems(request);
        for (FinanceReconciliationSourceImportItemRequest item : request.getItems()) {
            validateSourceImportItem(item);
        }
    }

    private void normalizeImportedSourceItems(FinanceReconciliationSourceImportRequest request) {
        String sourceSystem = request.getSourceSystem();
        if (sourceSystem == null || request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        for (FinanceReconciliationSourceImportItemRequest item : request.getItems()) {
            normalizeImportedSourceItem(sourceSystem, item);
        }
    }

    private void normalizeImportedSourceItem(String sourceSystem, FinanceReconciliationSourceImportItemRequest item) {
        if (item == null) {
            return;
        }

        Map<String, Object> rawPayload = item.getRawPayload();
        if (rawPayload == null || rawPayload.isEmpty()) {
            return;
        }

        if ("paymob".equals(sourceSystem)) {
            normalizePayMobImportItem(item, rawPayload);
        }
    }

    private void normalizePayMobImportItem(FinanceReconciliationSourceImportItemRequest item,
                                           Map<String, Object> rawPayload) {
        if (isBlank(item.getExternalReference())) {
            String externalReference = firstNestedText(rawPayload,
                    "providerEventId",
                    "transactionId",
                    "id",
                    "obj.id",
                    "order.id",
                    "obj.order.id");
            if (externalReference != null) {
                item.setExternalReference(externalReference);
            }
        }

        if (item.getSourceDate() == null) {
            LocalDate sourceDate = firstNestedDate(rawPayload,
                    "sourceDate",
                    "createdAt",
                    "created_at",
                    "obj.createdAt",
                    "obj.created_at");
            if (sourceDate != null) {
                item.setSourceDate(sourceDate);
            }
        }

        if (item.getAmount() == null) {
            BigDecimal amount = firstNestedAmount(rawPayload,
                    "amount",
                    "obj.amount",
                    "amount_cents",
                    "obj.amount_cents");
            if (amount != null) {
                item.setAmount(amount);
            }
        }

        if (item.getCurrencyCode() == null || item.getCurrencyCode().isBlank()) {
            String currencyCode = firstNestedText(rawPayload,
                    "currency",
                    "obj.currency");
            if (currencyCode != null) {
                item.setCurrencyCode(currencyCode.trim().toUpperCase(Locale.ROOT));
            }
        }

        if (item.getDescription() == null || item.getDescription().isBlank()) {
            String method = normalizePaymentMethod(firstNestedText(rawPayload,
                    "settlementMethod",
                    "paymentMethod",
                    "method",
                    "source_data.type",
                    "obj.source_data.type",
                    "source_data.sub_type",
                    "obj.source_data.sub_type"));
            if (method != null) {
                item.setDescription("PayMob " + method + " settlement");
            }
        }
    }

    private void validateSourceImportItem(FinanceReconciliationSourceImportItemRequest item) {
        if (item == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_ITEM_INVALID",
                    "Reconciliation source item is required");
        }
        item.setExternalReference(normalizeRequiredIdentifier(item.getExternalReference(),
                "FINANCE_RECONCILIATION_EXTERNAL_REFERENCE_INVALID", true));
        if (item.getSourceDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_DATE_REQUIRED",
                    "Reconciliation source item date is required");
        }
        if (item.getAmount() == null || item.getAmount().signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_AMOUNT_INVALID",
                    "Reconciliation source item amount must be zero or positive");
        }
        item.setAmount(item.getAmount().setScale(4, java.math.RoundingMode.HALF_UP));
        if (item.getCurrencyCode() == null || !item.getCurrencyCode().matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }
        if (item.getDescription() != null) {
            item.setDescription(item.getDescription().trim());
        }
    }

    private FinanceReconciliationRunItem requireRun(int companyId, UUID reconciliationRunId) {
        try {
            return dbFinanceReconciliation.getRunById(companyId, reconciliationRunId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_RECONCILIATION_RUN_NOT_FOUND",
                    "Finance reconciliation run was not found");
        }
    }

    private FinanceReconciliationItemItem requireItem(int companyId, UUID reconciliationItemId) {
        try {
            return dbFinanceReconciliation.getItemById(companyId, reconciliationItemId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_RECONCILIATION_ITEM_NOT_FOUND",
                    "Finance reconciliation item was not found");
        }
    }

    private List<String> mappingKeysForType(String reconciliationType) {
        return switch (reconciliationType) {
            case "cash_drawer" -> List.of("pos.cash", "payment.cash_drawer", "payment.cash_safe");
            case "card_settlement" -> List.of("pos.card", "payment.card_clearing", "payment.fee_expense");
            case "bank" -> List.of("payment.bank", "purchase.bank");
            case "inventory_valuation" -> List.of("inventory.asset", "purchase.inventory", "pos.inventory");
            case "supplier" -> List.of("purchase.payable", "purchase.grni");
            case "customer" -> List.of("pos.receivable");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        };
    }

    private void requireCompany(int companyId) {
        if (!dbFinanceSetup.companyExists(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_COMPANY_NOT_FOUND",
                    "Company does not exist");
        }
    }

    private void requireBranchIfPresent(int companyId, Integer branchId) {
        if (branchId != null && !dbFinanceSetup.branchBelongsToCompany(companyId, branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_BRANCH_INVALID",
                    "Branch does not belong to the company");
        }
    }

    private void authorizeRead(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.read");
    }

    private void authorizeEdit(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.edit");
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeRequiredIdentifier(String value, String errorCode, boolean required) {
        if (value == null || value.trim().isEmpty()) {
            if (required) {
                throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "Required identifier is missing");
            }
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("^[a-z0-9_.:-]{1,128}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode,
                    "Identifier may contain letters, numbers, underscore, dot, colon, or dash only");
        }
        return normalized;
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_PAYLOAD_INVALID",
                    "Reconciliation source item raw payload is not valid JSON");
        }
    }

    private Map<String, Object> parseRawPayload(String rawPayloadJson) {
        if (rawPayloadJson == null || rawPayloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawPayloadJson, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_PAYLOAD_INVALID",
                    "Reconciliation source item raw payload is not valid JSON");
        }
    }

    private Object nestedValue(Map<String, Object> payload, String path) {
        if (payload == null || path == null || path.isBlank()) {
            return null;
        }

        Object current = payload;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String importedSettlementSourceType(Map<String, Object> rawPayload) {
        String settlementMethod = importedSettlementMethod(rawPayload, null);
        if ("wallet".equals(settlementMethod)) {
            return "wallet_settlement";
        }
        if ("bank".equals(settlementMethod)) {
            return "bank_settlement";
        }
        return "card_settlement";
    }

    private String importedSettlementMethod(Map<String, Object> rawPayload, String sourceType) {
        String explicitMethod = normalizePaymentMethod(firstText(rawPayload, "settlementMethod", "paymentMethod", "method"));
        if (explicitMethod != null) {
            return explicitMethod;
        }
        if ("wallet_settlement".equals(sourceType)) {
            return "wallet";
        }
        if ("bank_settlement".equals(sourceType)) {
            return "bank";
        }
        return "card";
    }

    private String importedSettlementDestination(Map<String, Object> rawPayload, String settlementMethod) {
        String explicitDestination = normalizeDestination(firstText(rawPayload, "destination", "depositTo"));
        if (explicitDestination != null) {
            return explicitDestination;
        }
        return "cash".equals(settlementMethod) ? "safe" : "bank";
    }

    private String normalizePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("card".equals(normalized) || "visa".equals(normalized) || "mastercard".equals(normalized)) {
            return "card";
        }
        if ("wallet".equals(normalized) || "mobile_wallet".equals(normalized) || "instapay".equals(normalized)) {
            return "wallet";
        }
        if ("bank".equals(normalized) || "bank_transfer".equals(normalized) || "transfer".equals(normalized)) {
            return "bank";
        }
        if ("cash".equals(normalized)) {
            return "cash";
        }
        return normalized;
    }

    private String normalizeDestination(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("bank".equals(normalized) || "bank_account".equals(normalized) || "deposit_bank".equals(normalized)) {
            return "bank";
        }
        if ("cash".equals(normalized) || "drawer".equals(normalized)) {
            return "cash";
        }
        if ("safe".equals(normalized) || "cash_safe".equals(normalized) || "vault".equals(normalized)) {
            return "safe";
        }
        return normalized;
    }

    private BigDecimal firstPositiveAmount(Map<String, Object> rawPayload, String... keys) {
        for (String key : keys) {
            BigDecimal amount = amount(rawPayload.get(key));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return amount;
            }
        }
        return null;
    }

    private BigDecimal firstAmountOrZero(Map<String, Object> rawPayload, String... keys) {
        for (String key : keys) {
            BigDecimal amount = amount(rawPayload.get(key));
            if (amount != null) {
                return amount;
            }
        }
        return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal amount(Object value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value)).setScale(4, java.math.RoundingMode.HALF_UP);
            return amount.compareTo(BigDecimal.ZERO) < 0 ? null : amount;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal firstNestedAmount(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = nestedValue(payload, key);
            if (value == null) {
                continue;
            }
            BigDecimal amount = amount(value);
            if (amount == null) {
                continue;
            }
            if (key.endsWith("amount_cents")) {
                return amount.movePointLeft(2).setScale(4, java.math.RoundingMode.HALF_UP);
            }
            return amount;
        }
        return null;
    }

    private BigDecimal positiveOrNull(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String firstNestedText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = nestedValue(payload, key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private LocalDate firstNestedDate(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = nestedValue(payload, key);
            LocalDate date = parseDateValue(value);
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private LocalDate parseDateValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            // Try broader timestamp formats next.
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // Fall through to local datetime parsing.
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T')).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String settlementPostingSourceId(FinanceReconciliationSourceItem sourceItem) {
        return "reconciliation-source:" + sourceItem.getSourceSystem() + ":" + sourceItem.getExternalReference();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private int normalizeOffset(Integer offset) {
        return offset == null ? 0 : Math.max(offset, 0);
    }
}
