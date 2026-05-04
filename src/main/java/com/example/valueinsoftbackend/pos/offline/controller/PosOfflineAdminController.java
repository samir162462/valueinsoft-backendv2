package com.example.valueinsoftbackend.pos.offline.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.pos.offline.config.OfflinePosAdminProperties;
import com.example.valueinsoftbackend.pos.offline.config.OfflinePosWorkerProperties;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineAdminOperationRequest;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminBatchDetailsResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminBatchListItem;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminBatchListResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminImportDetailsResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminOnlineOrderReference;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminReadiness;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminOperationResponse;
import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;
import com.example.valueinsoftbackend.pos.offline.model.OfflineAdminImportDetailsSnapshot;
import com.example.valueinsoftbackend.pos.offline.model.OfflineImportStatusCounts;
import com.example.valueinsoftbackend.pos.offline.model.PosSyncBatchModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderErrorRepository;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import com.example.valueinsoftbackend.pos.offline.repository.PosSyncBatchRepository;
import com.example.valueinsoftbackend.pos.offline.repository.SyncAuditLogRepository;
import com.example.valueinsoftbackend.pos.offline.service.AuditLogService;
import com.example.valueinsoftbackend.pos.offline.service.PosOfflineSyncService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Controller for POS offline administrative operations.
 * Provides endpoints for manual recovery, processing, validation, and posting of offline synchronization batches.
 */
@RestController
@Validated
@RequestMapping("/api/admin/pos/offline-sync")
@Slf4j
public class PosOfflineAdminController {

    private static final String ADMIN_CAPABILITY = "pos.offline.admin.process";

    private final PosOfflineSyncService syncService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final OfflinePosWorkerProperties workerProperties;
    private final OfflinePosAdminProperties adminProperties;
    private final SyncAuditLogRepository auditLogRepository;
    private final OfflineOrderErrorRepository errorRepository;
    private final OfflineOrderImportRepository importRepository;
    private final PosSyncBatchRepository batchRepository;
    private final Gson gson = new Gson();

    /**
     * Constructs a new PosOfflineAdminController with required services.
     *
     * @param syncService          the service for handling offline synchronization
     * @param authorizationService the service for handling authorization checks
     * @param auditLogService      the service for logging audit events
     * @param workerProperties     configuration properties for offline workers
     */
    public PosOfflineAdminController(PosOfflineSyncService syncService,
                                     AuthorizationService authorizationService,
                                     AuditLogService auditLogService,
                                     OfflinePosWorkerProperties workerProperties,
                                     OfflinePosAdminProperties adminProperties,
                                     SyncAuditLogRepository auditLogRepository,
                                     OfflineOrderErrorRepository errorRepository,
                                     OfflineOrderImportRepository importRepository,
                                     PosSyncBatchRepository batchRepository) {
        this.syncService = syncService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.workerProperties = workerProperties;
        this.adminProperties = adminProperties;
        this.auditLogRepository = auditLogRepository;
        this.errorRepository = errorRepository;
        this.importRepository = importRepository;
        this.batchRepository = batchRepository;
    }

    @GetMapping("/batches")
    public ResponseEntity<OfflineAdminBatchListResponse> listBatches(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor,
            Principal principal) {
        authorize(principal, companyId, branchId);
        int pageSize = Math.min(Math.max(size != null ? size : 25, 1), 100);
        BatchCursor decodedCursor = decodeCursor(cursor);
        PosSyncBatchStatus statusFilter = parseBatchStatus(status);
        boolean effectiveActiveOnly = activeOnly && statusFilter == null;
        List<OfflineAdminBatchListItem> rows = batchRepository.findAdminBatchList(
                companyId,
                branchId,
                statusFilter,
                effectiveActiveOnly,
                decodedCursor.createdAt(),
                decodedCursor.batchId(),
                pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<OfflineAdminBatchListItem> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore && !items.isEmpty() ? encodeCursor(items.get(items.size() - 1)) : null;
        return ResponseEntity.ok(new OfflineAdminBatchListResponse(
                companyId,
                branchId,
                statusFilter != null ? statusFilter.name() : null,
                effectiveActiveOnly,
                pageSize,
                hasMore,
                nextCursor,
                List.copyOf(items)));
    }

    @GetMapping("/imports/{offlineOrderImportId}")
    public ResponseEntity<OfflineAdminImportDetailsResponse> importDetails(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long offlineOrderImportId,
            Principal principal) {
        authorize(principal, companyId, branchId);
        OfflineAdminImportDetailsSnapshot snapshot = importRepository
                .findAdminImportDetails(companyId, branchId, offlineOrderImportId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "OFFLINE_IMPORT_NOT_FOUND",
                        "Offline import was not found for the requested company and branch"));
        return ResponseEntity.ok(importDetailsResponse(snapshot));
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<OfflineAdminBatchDetailsResponse> batchDetails(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            Principal principal) {
        authorize(principal, companyId, branchId);
        return ResponseEntity.ok(batchDetailsResponse(companyId, branchId, batchId));
    }

    /**
     * Recovers stuck imports for a specific batch.
     *
     * @param companyId        the company ID
     * @param branchId         the branch ID
     * @param batchId          the batch ID to recover
     * @param thresholdMinutes optional threshold in minutes to consider an import as stuck
     * @param principal        the authenticated principal
     * @return a response entity containing the result of the recovery operation
     */
    @PostMapping("/batches/{batchId}/recover-stuck")
    public ResponseEntity<OfflineAdminOperationResponse> recoverStuck(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            @RequestParam(required = false) Integer thresholdMinutes,
            @RequestBody(required = false) OfflineAdminOperationRequest request,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        String reason = reason(request);
        if (reason == null) {
            String message = "Recover stuck requires a nonblank reason.";
            audit(companyId, branchId, batchId, "OFFLINE_ADMIN_RECOVER_STUCK_BLOCKED", principalName, null, message);
            return ResponseEntity.ok(response(companyId, branchId, batchId, "RECOVER_STUCK", false, message,
                    0, 0, false, List.of(message)));
        }
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_RECOVER_STUCK_REQUESTED", principalName, reason, null);
        int recovered = syncService.recoverStuckImports(
                companyId,
                branchId,
                batchId,
                thresholdMinutes != null ? thresholdMinutes : workerProperties.getStuckThresholdMinutes());
        return ResponseEntity.ok(response(companyId, branchId, batchId, "RECOVER_STUCK", true,
                "Operation completed", recovered, 0,
                true, List.of()));
    }

    /**
     * Triggers manual processing of pending imports for a specific batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID to process
     * @param principal the authenticated principal
     * @return a response entity containing the result of the processing operation
     */
    @PostMapping("/batches/{batchId}/process")
    public ResponseEntity<OfflineAdminOperationResponse> process(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            @RequestBody(required = false) OfflineAdminOperationRequest request,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_PROCESS_REQUESTED", principalName, reason(request), null);
        int processed = syncService.processPendingImports(companyId, branchId, batchId);
        return ResponseEntity.ok(response(companyId, branchId, batchId, "PROCESS", true,
                "Operation completed", processed, 0,
                true, List.of()));
    }

    /**
     * Triggers manual validation of ready imports for a specific batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID to validate
     * @param principal the authenticated principal
     * @return a response entity containing the result of the validation operation
     */
    @PostMapping("/batches/{batchId}/validate")
    public ResponseEntity<OfflineAdminOperationResponse> validate(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            @RequestBody(required = false) OfflineAdminOperationRequest request,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_VALIDATE_REQUESTED", principalName, reason(request), null);
        int validated = syncService.validateReadyImports(companyId, branchId, batchId);
        return ResponseEntity.ok(response(companyId, branchId, batchId, "VALIDATE", true,
                "Operation completed", validated, 0,
                true, List.of()));
    }

    /**
     * Triggers manual posting of validated imports for a specific batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID to post
     * @param principal the authenticated principal
     * @return a response entity containing the result of the posting operation
     */
    @PostMapping("/batches/{batchId}/post")
    public ResponseEntity<OfflineAdminOperationResponse> post(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            @RequestBody(required = false) OfflineAdminOperationRequest request,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        String reason = reason(request);
        OfflineImportStatusCounts beforeCounts = syncService.getImportStatusCounts(companyId, branchId, batchId);
        List<String> warnings = postingWarnings(beforeCounts);
        if (!adminProperties.isPostingEnabled()) {
            String message = "Admin offline posting is disabled by configuration.";
            List<String> blockedWarnings = withWarning(warnings, message);
            audit(companyId, branchId, batchId, "OFFLINE_ADMIN_POST_BLOCKED", principalName, reason, message);
            return ResponseEntity.ok(response(companyId, branchId, batchId, "POST", false, message,
                    0, Math.max(0, beforeCounts.totalCount() - beforeCounts.validatedCount()),
                    false, blockedWarnings));
        }
        if (reason == null) {
            String message = "Admin posting requires a nonblank reason.";
            List<String> blockedWarnings = withWarning(warnings, message);
            audit(companyId, branchId, batchId, "OFFLINE_ADMIN_POST_BLOCKED", principalName, null, message);
            return ResponseEntity.ok(response(companyId, branchId, batchId, "POST", false, message,
                    0, Math.max(0, beforeCounts.totalCount() - beforeCounts.validatedCount()),
                    false, blockedWarnings));
        }
        int maxPostBatchSize = Math.max(1, adminProperties.getMaxPostBatchSize());
        if (beforeCounts.validatedCount() > maxPostBatchSize) {
            String message = "Eligible posting count exceeds configured admin posting limit of " + maxPostBatchSize + ".";
            List<String> blockedWarnings = withWarning(warnings, message);
            audit(companyId, branchId, batchId, "OFFLINE_ADMIN_POST_BLOCKED", principalName, reason, message);
            return ResponseEntity.ok(response(companyId, branchId, batchId, "POST", false, message,
                    0, Math.max(0, beforeCounts.totalCount() - beforeCounts.validatedCount()),
                    false, blockedWarnings));
        }
        if (forceRequired(beforeCounts) && !force(request)) {
            String message = "Force confirmation is required because the batch has posting safety warnings.";
            List<String> blockedWarnings = withWarning(warnings, message);
            audit(companyId, branchId, batchId, "OFFLINE_ADMIN_POST_BLOCKED", principalName, reason, message);
            return ResponseEntity.ok(response(companyId, branchId, batchId, "POST", false, message,
                    0, Math.max(0, beforeCounts.totalCount() - beforeCounts.validatedCount()),
                    false, blockedWarnings));
        }
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_POST_REQUESTED", principalName, reason, null);
        int posted = syncService.postValidatedImports(companyId, branchId, batchId);
        int skipped = Math.max(0, beforeCounts.totalCount() - beforeCounts.validatedCount());
        return ResponseEntity.ok(response(companyId, branchId, batchId, "POST", true,
                "Operation completed", posted, skipped, true, warnings));
    }

    @PostMapping("/batches/{batchId}/post-preview")
    public ResponseEntity<OfflineAdminOperationResponse> postPreview(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_POST_PREVIEW_REQUESTED", principalName, null, null);
        OfflineImportStatusCounts counts = syncService.getImportStatusCounts(companyId, branchId, batchId);
        int skipped = Math.max(0, counts.totalCount() - counts.validatedCount());
        return ResponseEntity.ok(response(companyId, branchId, batchId, "POST_PREVIEW", true,
                "Preview completed", 0, skipped,
                false, previewWarnings(counts)));
    }

    /**
     * Recalculates the summary statistics for a specific batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID to recalculate
     * @param principal the authenticated principal
     * @return a response entity indicating completion
     */
    @PostMapping("/batches/{batchId}/recalculate-summary")
    public ResponseEntity<OfflineAdminOperationResponse> recalculateSummary(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            @RequestBody(required = false) OfflineAdminOperationRequest request,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_RECALCULATE_REQUESTED", principalName, reason(request), null);
        syncService.recalculateBatchSummary(companyId, branchId, batchId);
        return ResponseEntity.ok(response(companyId, branchId, batchId, "RECALCULATE_SUMMARY", true,
                "Operation completed", 0, 0,
                true, List.of()));
    }

    /**
     * Helper method to create an admin operation response.
     *
     * @param companyId      the company ID
     * @param branchId       the branch ID
     * @param batchId        the batch ID
     * @param operation      the operation name
     * @param processedCount the number of items processed
     * @return a new OfflineAdminOperationResponse object
     */
    private OfflineAdminOperationResponse response(Long companyId, Long branchId, Long batchId,
                                                   String operation, boolean accepted, String message,
                                                   int processedCount, int skippedCount,
                                                   boolean summaryRecalculated, List<String> warnings) {
        PosSyncBatchModel batch = syncService.getBatch(companyId, branchId, batchId);
        OfflineImportStatusCounts counts = syncService.getImportStatusCounts(companyId, branchId, batchId);
        return new OfflineAdminOperationResponse(
                companyId,
                branchId,
                batchId,
                operation,
                accepted,
                batch.status().name(),
                message,
                processedCount,
                counts.syncedCount(),
                skippedCount,
                counts.failedCount(),
                counts.validationFailedCount(),
                counts.postingFailedCount(),
                counts.needsReviewCount(),
                counts.validatedCount(),
                summaryRecalculated,
                List.copyOf(warnings));
    }

    /**
     * Logs an audit event for an admin operation.
     *
     * @param companyId     the company ID
     * @param branchId      the branch ID
     * @param batchId       the batch ID
     * @param eventType     the type of event
     * @param principalName the name of the performing user
     */
    private void audit(Long companyId, Long branchId, Long batchId, String eventType,
                       String principalName, String reason, String blockReason) {
        try {
            auditLogService.logSyncEvent(
                    companyId,
                    branchId,
                    batchId,
                    null,
                    null,
                    null,
                    eventType,
                    "Offline admin operation requested by " + principalName,
                    auditMetadata(reason, blockReason));
        } catch (Exception ex) {
            log.warn("Offline admin audit write failed: eventType={}, companyId={}, branchId={}, batchId={}",
                    eventType, companyId, branchId, batchId, ex);
        }
        log.info("Offline admin operation requested: eventType={}, companyId={}, branchId={}, batchId={}, principal={}, blocked={}",
                eventType, companyId, branchId, batchId, principalName, blockReason != null);
    }

    private List<String> previewWarnings(OfflineImportStatusCounts counts) {
        List<String> warnings = postingWarnings(counts);
        warnings.add("Preview is read-only; decimal amount/quantity and multi-tender checks still run during actual posting.");
        return warnings;
    }

    private List<String> postingWarnings(OfflineImportStatusCounts counts) {
        List<String> warnings = new ArrayList<>();
        if (counts.postingCount() > 0) {
            warnings.add("Batch contains POSTING imports; review them before retrying any stuck posting rows.");
        }
        if (counts.needsReviewCount() > 0) {
            warnings.add("Batch contains NEEDS_REVIEW imports requiring manual review.");
        }
        if (counts.validatedCount() == 0) {
            warnings.add("No VALIDATED imports are currently eligible for posting.");
        }
        return warnings;
    }

    private OfflineAdminBatchDetailsResponse batchDetailsResponse(Long companyId, Long branchId, Long batchId) {
        PosSyncBatchModel batch = syncService.getBatch(companyId, branchId, batchId);
        OfflineImportStatusCounts counts = syncService.getImportStatusCounts(companyId, branchId, batchId);
        List<String> warnings = detailsWarnings(counts);
        return new OfflineAdminBatchDetailsResponse(
                companyId,
                branchId,
                batchId,
                batch.status().name(),
                batch.createdAt(),
                batch.syncStartedAt(),
                batch.syncCompletedAt(),
                counts,
                batch.totalOrders(),
                batch.syncedOrders(),
                batch.failedOrders(),
                batch.duplicateOrders(),
                batch.needsReviewOrders(),
                counts.validatedCount(),
                warnings,
                readiness(counts),
                auditLogRepository.findRecentAdminEvents(companyId, branchId, batchId, 10),
                errorRepository.summarizeErrorsByBatchId(companyId, branchId, batchId));
    }

    private OfflineAdminImportDetailsResponse importDetailsResponse(OfflineAdminImportDetailsSnapshot snapshot) {
        return new OfflineAdminImportDetailsResponse(
                snapshot.companyId(),
                snapshot.branchId(),
                snapshot.batchId(),
                snapshot.offlineOrderImportId(),
                snapshot.offlineOrderNo(),
                snapshot.status(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.processingStartedAt(),
                snapshot.postingStartedAt(),
                snapshot.postingCompletedAt(),
                snapshot.postedOrderId(),
                snapshot.officialOrderId(),
                snapshot.financePostingRequestId(),
                snapshot.financeJournalEntryId(),
                snapshot.financeEnqueueStatus(),
                compact(snapshot.financeEnqueueError()),
                snapshot.errorCode(),
                compact(snapshot.errorMessage()),
                snapshot.retryCount(),
                snapshot.lastRetryAt(),
                snapshot.deviceId(),
                snapshot.deviceCode(),
                snapshot.cashierId(),
                maskIdempotencyKey(snapshot.idempotencyKey()),
                hashPrefix(snapshot.payloadHash()),
                snapshot.idempotencyStatus(),
                compactMetadata(snapshot.idempotencyResultMetadata()),
                onlineOrderReference(snapshot),
                errorRepository.findRecentImportErrors(
                        snapshot.companyId(), snapshot.branchId(), snapshot.offlineOrderImportId(), 10),
                auditLogRepository.findRecentImportEvents(
                        snapshot.companyId(), snapshot.branchId(), snapshot.offlineOrderImportId(), 10));
    }

    private OfflineAdminOnlineOrderReference onlineOrderReference(OfflineAdminImportDetailsSnapshot snapshot) {
        Long orderId = snapshot.postedOrderId() != null ? snapshot.postedOrderId() : snapshot.officialOrderId();
        if (orderId == null) {
            return null;
        }
        return new OfflineAdminOnlineOrderReference(
                snapshot.postedOrderId(),
                snapshot.officialOrderId(),
                snapshot.branchId(),
                TenantSqlIdentifiers.orderTable(
                        toInteger(snapshot.companyId(), "companyId"),
                        toInteger(snapshot.branchId(), "branchId")));
    }

    private OfflineAdminReadiness readiness(OfflineImportStatusCounts counts) {
        List<String> recoverBlockedReasons = new ArrayList<>();
        if (counts.processingCount() + counts.validatingCount() + counts.postingCount() == 0) {
            recoverBlockedReasons.add("NO_STUCK_IMPORTS");
        }

        List<String> processBlockedReasons = new ArrayList<>();
        if (counts.pendingCount() + counts.pendingRetryCount() == 0) {
            processBlockedReasons.add("NO_PENDING_IMPORTS");
        }

        List<String> validateBlockedReasons = new ArrayList<>();
        if (counts.readyForValidationCount() == 0) {
            validateBlockedReasons.add("NO_READY_FOR_VALIDATION_IMPORTS");
        }

        List<String> postBlockedReasons = postBlockedReasons(counts);
        boolean requiresForceForPost = forceRequired(counts);
        return new OfflineAdminReadiness(
                recoverBlockedReasons.isEmpty(),
                processBlockedReasons.isEmpty(),
                validateBlockedReasons.isEmpty(),
                postBlockedReasons.isEmpty(),
                true,
                List.copyOf(recoverBlockedReasons),
                List.copyOf(processBlockedReasons),
                List.copyOf(validateBlockedReasons),
                List.copyOf(postBlockedReasons),
                true,
                true,
                requiresForceForPost);
    }

    private List<String> postBlockedReasons(OfflineImportStatusCounts counts) {
        List<String> reasons = new ArrayList<>();
        if (!adminProperties.isPostingEnabled()) {
            reasons.add("ADMIN_POSTING_DISABLED");
        }
        if (counts.validatedCount() == 0) {
            reasons.add("NO_VALIDATED_IMPORTS");
        }
        if (counts.postingCount() > 0) {
            reasons.add("POSTING_ROWS_EXIST");
        }
        if (counts.needsReviewCount() > 0) {
            reasons.add("NEEDS_REVIEW_ROWS_EXIST");
        }
        if (counts.validatedCount() > Math.max(1, adminProperties.getMaxPostBatchSize())) {
            reasons.add("MAX_POST_BATCH_SIZE_EXCEEDED");
        }
        return reasons;
    }

    private List<String> detailsWarnings(OfflineImportStatusCounts counts) {
        List<String> warnings = previewWarnings(counts);
        if (!adminProperties.isPostingEnabled()) {
            warnings.add("Admin posting is disabled by configuration.");
        }
        if (counts.validatedCount() > Math.max(1, adminProperties.getMaxPostBatchSize())) {
            warnings.add("Eligible posting count exceeds configured admin posting limit.");
        }
        return warnings;
    }

    private String reason(OfflineAdminOperationRequest request) {
        return request == null ? null : request.normalizedReason();
    }

    private boolean force(OfflineAdminOperationRequest request) {
        return request != null && request.forceRequested();
    }

    private boolean forceRequired(OfflineImportStatusCounts counts) {
        return counts.postingCount() > 0 || counts.needsReviewCount() > 0;
    }

    private List<String> withWarning(List<String> warnings, String warning) {
        List<String> result = new ArrayList<>(warnings);
        result.add(warning);
        return result;
    }

    private String maskIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private String hashPrefix(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);
    }

    private String compactMetadata(String value) {
        return compact(value, 1000);
    }

    private String compact(String value) {
        return compact(value, 300);
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(1, maxLength));
    }

    private String auditMetadata(String reason, String blockReason) {
        if (reason == null && blockReason == null) {
            return null;
        }
        Map<String, String> metadata = new java.util.LinkedHashMap<>();
        if (reason != null) {
            metadata.put("reason", reason);
        }
        if (blockReason != null) {
            metadata.put("blockReason", blockReason);
        }
        return gson.toJson(metadata);
    }

    private PosSyncBatchStatus parseBatchStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PosSyncBatchStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH_STATUS", "Unsupported batch status filter");
        }
    }

    private BatchCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return BatchCursor.empty();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor.trim()), java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor");
            }
            return new BatchCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH_CURSOR", "Invalid batch list cursor");
        }
    }

    private String encodeCursor(OfflineAdminBatchListItem item) {
        if (item == null || item.createdAt() == null || item.batchId() == null) {
            return null;
        }
        String raw = item.createdAt() + "|" + item.batchId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record BatchCursor(Instant createdAt, Long batchId) {
        static BatchCursor empty() {
            return new BatchCursor(null, null);
        }
    }

    /**
     * Authorizes the admin request by checking capabilities.
     *
     * @param principal the authenticated principal
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @return the principal name
     */
    private String authorize(Principal principal, Long companyId, Long branchId) {
        String principalName = principalName(principal);
        authorizationService.assertAuthenticatedCapability(
                principalName,
                toInteger(companyId, "companyId"),
                toInteger(branchId, "branchId"),
                ADMIN_CAPABILITY);
        return principalName;
    }

    /**
     * Extracts and validates the principal name.
     *
     * @param principal the principal to check
     * @return the principal name
     * @throws ApiException if authentication fails
     */
    private String principalName(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return principal.getName();
    }

    /**
     * Safely converts Long to Integer.
     *
     * @param value     the long value
     * @param fieldName the field name for error reporting
     * @return the integer value
     * @throws ApiException if value is out of range
     */
    private Integer toInteger(Long value, String fieldName) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_ACCESS", fieldName + " is out of range");
        }
    }
}
