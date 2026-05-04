package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processor responsible for the initial "skeleton" processing of offline order imports.
 * It ensures that the import record matches an existing idempotency record and transitions
 * the status to READY_FOR_VALIDATION.
 */
@Service
@Slf4j
public class OfflineSingleOrderProcessor {

    private final OfflineOrderImportRepository importRepo;
    private final PosIdempotencyService idempotencyService;
    private final SyncErrorService syncErrorService;
    private final AuditLogService auditLogService;

    /**
     * Constructs a new OfflineSingleOrderProcessor with required dependencies.
     *
     * @param importRepo         the repository for offline order imports
     * @param idempotencyService the service for verifying idempotency
     * @param syncErrorService   the service for logging synchronization errors
     * @param auditLogService    the service for logging audit events
     */
    public OfflineSingleOrderProcessor(OfflineOrderImportRepository importRepo,
                                       PosIdempotencyService idempotencyService,
                                       SyncErrorService syncErrorService,
                                       AuditLogService auditLogService) {
        this.importRepo = importRepo;
        this.idempotencyService = idempotencyService;
        this.syncErrorService = syncErrorService;
        this.auditLogService = auditLogService;
    }

    /**
     * Claims and processes the next pending import record for a batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID
     * @return true if a record was processed, false otherwise
     */
    @Transactional
    public boolean processNextPendingImport(Long companyId, Long branchId, Long batchId) {
        Optional<OfflineOrderImportModel> claimed = importRepo.claimNextPendingImport(companyId, branchId, batchId);
        if (claimed.isEmpty()) {
            auditLogService.logSyncEvent(
                    companyId, branchId, batchId, null, null, null,
                    "OFFLINE_ORDER_PROCESSING_SKIPPED",
                    "No PENDING or PENDING_RETRY import was available to claim",
                    null);
            return false;
        }
        processClaimedImport(claimed.get());
        return true;
    }

    /**
     * Claims and processes a specific import record by ID.
     *
     * @param companyId            the company ID
     * @param branchId             the branch ID
     * @param offlineOrderImportId the ID of the import record
     * @return true if the record was successfully claimed and processed
     */
    @Transactional
    public boolean processSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
        Optional<OfflineOrderImportModel> claimed =
                importRepo.claimImportForProcessing(companyId, branchId, offlineOrderImportId);
        if (claimed.isEmpty()) {
            auditLogService.logSyncEvent(
                    companyId, branchId, null, offlineOrderImportId, null, null,
                    "OFFLINE_ORDER_PROCESSING_SKIPPED",
                    "Import was already claimed or is not eligible for processing",
                    null);
            return false;
        }
        processClaimedImport(claimed.get());
        return true;
    }

    /**
     * Executes the skeleton processing logic for a claimed import record.
     *
     * @param importRecord the claimed import record
     */
    private void processClaimedImport(OfflineOrderImportModel importRecord) {
        Long companyId = importRecord.companyId();
        Long branchId = importRecord.branchId();

        auditLogService.logSyncEvent(
                companyId, branchId, importRecord.syncBatchId(), importRecord.id(),
                importRecord.deviceId(), importRecord.cashierId(),
                "OFFLINE_ORDER_PROCESSING_STARTED",
                "Offline import claimed for skeleton processing",
                null);

        try {
            idempotencyService.requireMatchingRecord(
                    companyId,
                    branchId,
                    importRecord.deviceId(),
                    importRecord.idempotencyKey(),
                    importRecord.payloadHash());

            // Initial processing phase completes by marking the record as ready for detailed validation.
            importRepo.markReadyForValidation(companyId, branchId, importRecord.id());
            auditLogService.logSyncEvent(
                    companyId, branchId, importRecord.syncBatchId(), importRecord.id(),
                    importRecord.deviceId(), importRecord.cashierId(),
                    "OFFLINE_ORDER_PROCESSING_COMPLETED_PLACEHOLDER",
                    "Import marked READY_FOR_VALIDATION; posting is deferred",
                    null);
        } catch (OfflineSyncException ex) {
            markFailed(importRecord, ex.getErrorCode(), ex.getMessage(), errorSeverity(ex));
        } catch (Exception ex) {
            log.error("Unexpected offline import processing failure: importId={}", importRecord.id(), ex);
            markFailed(importRecord, "OFFLINE_PROCESSING_UNEXPECTED_ERROR",
                    "Unexpected processing failure: " + ex.getMessage(), OfflineErrorSeverity.SYSTEM_ERROR);
        }
    }

    /**
     * Determines the error severity for a synchronization exception.
     *
     * @param ex the exception
     * @return the severity level
     */
    private OfflineErrorSeverity errorSeverity(OfflineSyncException ex) {
        if ("IDEMPOTENCY_PAYLOAD_MISMATCH".equals(ex.getErrorCode())) {
            return OfflineErrorSeverity.HARD_FAIL;
        }
        return OfflineErrorSeverity.SYSTEM_ERROR;
    }

    /**
     * Marks an import as failed and logs the error details.
     *
     * @param importRecord the import record
     * @param errorCode    the error code
     * @param errorMessage the error message
     * @param severity     the error severity
     */
    private void markFailed(OfflineOrderImportModel importRecord, String errorCode, String errorMessage,
                            OfflineErrorSeverity severity) {
        importRepo.markProcessingFailed(
                importRecord.companyId(), importRecord.branchId(), importRecord.id(), errorCode, errorMessage);
        syncErrorService.saveError(
                importRecord.id(),
                importRecord.companyId(),
                importRecord.branchId(),
                "PROCESSING_SKELETON",
                errorCode,
                errorMessage,
                null,
                null,
                severity,
                false,
                true);
        auditLogService.logSyncEvent(
                importRecord.companyId(), importRecord.branchId(), importRecord.syncBatchId(), importRecord.id(),
                importRecord.deviceId(), importRecord.cashierId(),
                "OFFLINE_ORDER_PROCESSING_FAILED",
                "Import failed during skeleton processing: " + errorCode,
                null);
    }
}
