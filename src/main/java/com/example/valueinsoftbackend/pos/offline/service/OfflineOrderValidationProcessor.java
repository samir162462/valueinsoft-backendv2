package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Processor responsible for orchestrating the validation of offline order imports.
 * It claims eligible records, invokes the validation service, and persists any errors found.
 */
@Service
@Slf4j
public class OfflineOrderValidationProcessor {

    private final OfflineOrderImportRepository importRepo;
    private final OfflineOrderImportValidationService validationService;
    private final SyncErrorService syncErrorService;
    private final AuditLogService auditLogService;

    /**
     * Constructs a new OfflineOrderValidationProcessor with required dependencies.
     *
     * @param importRepo        the repository for offline order imports
     * @param validationService the service performing granular business validation
     * @param syncErrorService  the service for logging synchronization errors
     * @param auditLogService   the service for logging audit events
     */
    public OfflineOrderValidationProcessor(OfflineOrderImportRepository importRepo,
                                           OfflineOrderImportValidationService validationService,
                                           SyncErrorService syncErrorService,
                                           AuditLogService auditLogService) {
        this.importRepo = importRepo;
        this.validationService = validationService;
        this.syncErrorService = syncErrorService;
        this.auditLogService = auditLogService;
    }

    /**
     * Claims and validates the next available import record for a specific batch.
     * This method is transactional to ensure atomic claiming and status updates.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID
     * @return true if a record was processed, false otherwise
     */
    @Transactional
    public boolean validateNextReadyImport(Long companyId, Long branchId, Long batchId) {
        Optional<OfflineOrderImportModel> claimed = importRepo.claimNextReadyForValidation(companyId, branchId, batchId);
        if (claimed.isEmpty()) {
            auditLogService.logSyncEvent(
                    companyId, branchId, batchId, null, null, null,
                    "OFFLINE_ORDER_VALIDATION_SKIPPED",
                    "No READY_FOR_VALIDATION import was available to claim",
                    null);
            return false;
        }
        validateClaimedImport(claimed.get());
        return true;
    }

    /**
     * Claims and validates a specific import record by ID.
     *
     * @param companyId            the company ID
     * @param branchId             the branch ID
     * @param offlineOrderImportId the ID of the import record
     * @return true if the record was successfully claimed and processed
     */
    @Transactional
    public boolean validateSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
        Optional<OfflineOrderImportModel> claimed =
                importRepo.claimImportForValidation(companyId, branchId, offlineOrderImportId);
        if (claimed.isEmpty()) {
            auditLogService.logSyncEvent(
                    companyId, branchId, null, offlineOrderImportId, null, null,
                    "OFFLINE_ORDER_VALIDATION_SKIPPED",
                    "Import was already claimed or is not eligible for validation",
                    null);
            return false;
        }
        validateClaimedImport(claimed.get());
        return true;
    }

    /**
     * Executes the validation logic for a claimed import record.
     *
     * @param importRecord the claimed import record
     */
    private void validateClaimedImport(OfflineOrderImportModel importRecord) {
        Long companyId = importRecord.companyId();
        Long branchId = importRecord.branchId();
        auditLogService.logSyncEvent(
                companyId, branchId, importRecord.syncBatchId(), importRecord.id(),
                importRecord.deviceId(), importRecord.cashierId(),
                "OFFLINE_ORDER_VALIDATION_STARTED",
                "Offline import validation started",
                null);

        try {
            List<OfflineOrderImportValidationService.ValidationError> errors = validationService.validate(importRecord);
            if (errors.isEmpty()) {
                importRepo.markValidated(companyId, branchId, importRecord.id());
                auditLogService.logSyncEvent(
                        companyId, branchId, importRecord.syncBatchId(), importRecord.id(),
                        importRecord.deviceId(), importRecord.cashierId(),
                        "OFFLINE_ORDER_VALIDATION_PASSED",
                        "Offline import validated; posting is deferred",
                        null);
                return;
            }

            for (OfflineOrderImportValidationService.ValidationError error : errors) {
                syncErrorService.saveError(
                        importRecord.id(),
                        companyId,
                        branchId,
                        "VALIDATION",
                        error.code(),
                        error.message(),
                        error.fieldPath(),
                        null,
                        severity(error.code()),
                        false,
                        managerReviewRequired(error.code()));
            }
            OfflineOrderImportValidationService.ValidationError first = errors.get(0);
            importRepo.markValidationFailed(companyId, branchId, importRecord.id(), first.code(), first.message());
            auditLogService.logSyncEvent(
                    companyId, branchId, importRecord.syncBatchId(), importRecord.id(),
                    importRecord.deviceId(), importRecord.cashierId(),
                    "OFFLINE_ORDER_VALIDATION_FAILED",
                    "Offline import validation failed with " + errors.size() + " error(s)",
                    null);
        } catch (Exception ex) {
            log.error("Unexpected offline import validation failure: importId={}", importRecord.id(), ex);
            String message = "Unexpected validation failure: " + ex.getMessage();
            importRepo.markValidationFailed(
                    companyId, branchId, importRecord.id(), "OFFLINE_ORDER_VALIDATION_UNEXPECTED_ERROR", message);
            syncErrorService.saveError(
                    importRecord.id(),
                    companyId,
                    branchId,
                    "VALIDATION",
                    "OFFLINE_ORDER_VALIDATION_UNEXPECTED_ERROR",
                    message,
                    null,
                    null,
                    OfflineErrorSeverity.SYSTEM_ERROR,
                    false,
                    true);
            auditLogService.logSyncEvent(
                    companyId, branchId, importRecord.syncBatchId(), importRecord.id(),
                    importRecord.deviceId(), importRecord.cashierId(),
                    "OFFLINE_ORDER_VALIDATION_FAILED",
                    "Offline import validation failed unexpectedly",
                    null);
        }
    }

    /**
     * Determines the severity of a validation error.
     *
     * @param errorCode the error code
     * @return the severity level
     */
    private OfflineErrorSeverity severity(String errorCode) {
        if ("IDEMPOTENCY_PAYLOAD_MISMATCH".equals(errorCode)) {
            return OfflineErrorSeverity.HARD_FAIL;
        }
        return OfflineErrorSeverity.NEEDS_REVIEW;
    }

    /**
     * Determines if a validation error requires manager review.
     *
     * @param errorCode the error code
     * @return true if review is required
     */
    private boolean managerReviewRequired(String errorCode) {
        return !"OFFLINE_INVALID_QUANTITY".equals(errorCode)
                && !"OFFLINE_INVALID_PRICE".equals(errorCode);
    }
}
