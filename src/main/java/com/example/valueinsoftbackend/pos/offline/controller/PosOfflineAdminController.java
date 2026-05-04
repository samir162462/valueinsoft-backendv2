package com.example.valueinsoftbackend.pos.offline.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.pos.offline.config.OfflinePosWorkerProperties;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminOperationResponse;
import com.example.valueinsoftbackend.pos.offline.service.AuditLogService;
import com.example.valueinsoftbackend.pos.offline.service.PosOfflineSyncService;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

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
                                     OfflinePosWorkerProperties workerProperties) {
        this.syncService = syncService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.workerProperties = workerProperties;
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
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_RECOVER_STUCK_REQUESTED", principalName);
        int recovered = syncService.recoverStuckImports(
                companyId,
                branchId,
                batchId,
                thresholdMinutes != null ? thresholdMinutes : workerProperties.getStuckThresholdMinutes());
        return ResponseEntity.ok(response(companyId, branchId, batchId, "RECOVER_STUCK", recovered));
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
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_PROCESS_REQUESTED", principalName);
        int processed = syncService.processPendingImports(companyId, branchId, batchId);
        return ResponseEntity.ok(response(companyId, branchId, batchId, "PROCESS", processed));
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
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_VALIDATE_REQUESTED", principalName);
        int validated = syncService.validateReadyImports(companyId, branchId, batchId);
        return ResponseEntity.ok(response(companyId, branchId, batchId, "VALIDATE", validated));
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
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_POST_REQUESTED", principalName);
        int posted = syncService.postValidatedImports(companyId, branchId, batchId);
        return ResponseEntity.ok(response(companyId, branchId, batchId, "POST", posted));
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
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId);
        audit(companyId, branchId, batchId, "OFFLINE_ADMIN_RECALCULATE_REQUESTED", principalName);
        syncService.recalculateBatchSummary(companyId, branchId, batchId);
        return ResponseEntity.ok(response(companyId, branchId, batchId, "RECALCULATE_SUMMARY", 0));
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
                                                   String operation, int processedCount) {
        return new OfflineAdminOperationResponse(
                companyId,
                branchId,
                batchId,
                operation,
                true,
                "Operation completed",
                processedCount,
                0,
                true);
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
    private void audit(Long companyId, Long branchId, Long batchId, String eventType, String principalName) {
        auditLogService.logSyncEvent(
                companyId,
                branchId,
                batchId,
                null,
                null,
                null,
                eventType,
                "Offline admin operation requested by " + principalName,
                null);
        log.info("Offline admin operation requested: eventType={}, companyId={}, branchId={}, batchId={}, principal={}",
                eventType, companyId, branchId, batchId, principalName);
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
