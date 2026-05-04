package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineSyncUploadRequest;
import com.example.valueinsoftbackend.pos.offline.dto.response.*;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.model.PosSyncBatchModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import com.example.valueinsoftbackend.pos.offline.repository.PosSyncBatchRepository;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Facade service for orchestrating the POS offline synchronization lifecycle.
 * It manages batch reception, per-import processing, validation, and final posting
 * while ensuring data integrity through idempotency and auditing.
 */
@Service
@Slf4j
public class PosOfflineSyncService {

        private final PosSyncBatchRepository batchRepo;
        private final OfflineOrderImportRepository importRepo;
        private final PosDeviceService deviceService;
        private final OfflineOrderValidationService validationService;
        private final PosIdempotencyService idempotencyService;
        private final SyncErrorService syncErrorService;
        private final AuditLogService auditLogService;
        private final OfflineSingleOrderProcessor singleOrderProcessor;
        private final OfflineOrderValidationProcessor validationProcessor;
        private final OfflineOrderPostingProcessor postingProcessor;
        private final OfflinePosProperties props;
        private final Gson gson = new Gson();

        /**
         * Constructs a new PosOfflineSyncService with all required internal processors and services.
         *
         * @param batchRepo           the repository for sync batches
         * @param importRepo          the repository for individual order imports
         * @param deviceService       the service for hardware eligibility checks
         * @param validationService   the service for batch-level validation
         * @param idempotencyService  the service for duplicate prevention
         * @param syncErrorService    the service for error logging
         * @param auditLogService     the service for lifecycle auditing
         * @param singleOrderProcessor the processor for the initial skeleton phase
         * @param validationProcessor the processor for the business validation phase
         * @param postingProcessor    the processor for the final database posting phase
         * @param props               the configuration properties for offline sync
         */
        public PosOfflineSyncService(PosSyncBatchRepository batchRepo,
                        OfflineOrderImportRepository importRepo,
                        PosDeviceService deviceService,
                        OfflineOrderValidationService validationService,
                        PosIdempotencyService idempotencyService,
                        SyncErrorService syncErrorService,
                        AuditLogService auditLogService,
                        OfflineSingleOrderProcessor singleOrderProcessor,
                        OfflineOrderValidationProcessor validationProcessor,
                        OfflineOrderPostingProcessor postingProcessor,
                        OfflinePosProperties props) {
                this.batchRepo = batchRepo;
                this.importRepo = importRepo;
                this.deviceService = deviceService;
                this.validationService = validationService;
                this.idempotencyService = idempotencyService;
                this.syncErrorService = syncErrorService;
                this.auditLogService = auditLogService;
                this.singleOrderProcessor = singleOrderProcessor;
                this.validationProcessor = validationProcessor;
                this.postingProcessor = postingProcessor;
                this.props = props;
        }

        /**
         * Receives an offline sync upload, validates basic fields,
         * stores each order as a raw import record, and returns a
         * RECEIVED status. Processing is handled through separate per-import boundaries.
         *
         * @param request       the upload request payload
         * @param principalName the name of the authenticated user
         * @return the result of the batch upload reception
         * @throws OfflineSyncException if sync is disabled, the batch is too large, or hardware is ineligible
         */
        public OfflineSyncUploadResponse uploadOfflineSync(OfflineSyncUploadRequest request, String principalName) {
                // 1. Global feature gate
                if (!props.isAllowOfflineSync()) {
                        throw new OfflineSyncException("OFFLINE_SYNC_DISABLED",
                                        "Offline sync is currently disabled");
                }

                // 2. Validate batch size limits
                if (request.orders().size() > props.getMaxOrdersPerBatch()) {
                        throw new OfflineSyncException("BATCH_TOO_LARGE",
                                        "Batch contains " + request.orders().size()
                                                        + " orders, max allowed is " + props.getMaxOrdersPerBatch());
                }

                // 3. Validate device eligibility
                deviceService.validateDeviceForOfflineSync(
                                request.companyId(), request.branchId(), request.deviceId());

                // 4. Validate batch basic fields
                validationService.validateBatch(request);

                // 5. Create sync batch record
                Long batchId = batchRepo.insertBatch(
                                request.companyId(), request.branchId(),
                                request.deviceId(), request.cashierId(),
                                request.clientBatchId(), request.clientType().name(),
                                request.platform(), request.appVersion(),
                                request.orders().size(),
                                request.offlineStartedAt(), request.syncStartedAt());

                log.info("Sync batch created: batchId={}, clientBatchId={}, totalOrders={}",
                                batchId, request.clientBatchId(), request.orders().size());

                // 6. Store each offline order raw payload
                List<OfflineOrderSyncResult> results = new ArrayList<>();
                for (OfflineOrderRequest order : request.orders()) {
                        OfflineOrderSyncResult result = storeOfflineOrder(
                                        batchId, request, order);
                        results.add(result);
                }

                // 7. Log the sync event
                auditLogService.logSyncEvent(
                                request.companyId(), request.branchId(),
                                batchId, null, request.deviceId(), request.cashierId(),
                                "BATCH_RECEIVED",
                                "Received batch with " + request.orders().size() + " orders from " + principalName,
                                null);

                return new OfflineSyncUploadResponse(
                                batchId, request.clientBatchId(),
                                PosSyncBatchStatus.RECEIVED,
                                request.orders().size(),
                                0, 0, 0, 0,
                                results);
        }

        /**
         * Executes the initial "skeleton" processing for all pending imports in a batch.
         * This transitions records from PENDING to READY_FOR_VALIDATION.
         *
         * @param companyId the company ID
         * @param branchId  the branch ID
         * @param batchId   the batch ID
         * @return the number of records successfully processed
         */
        public int processPendingImports(Long companyId, Long branchId, Long batchId) {
                batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                int processed = 0;
                while (singleOrderProcessor.processNextPendingImport(companyId, branchId, batchId)) {
                        processed++;
                }
                batchRepo.recalculateSummary(companyId, branchId, batchId);
                return processed;
        }

        /**
         * Processes a specific import record through the initial skeleton phase.
         *
         * @param companyId            the company ID
         * @param branchId             the branch ID
         * @param offlineOrderImportId the ID of the import record
         * @return true if the record was processed
         */
        public boolean processSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
                boolean processed = singleOrderProcessor.processSingleImport(companyId, branchId, offlineOrderImportId);
                importRepo.findByImportId(companyId, branchId, offlineOrderImportId)
                                .ifPresent(importRecord -> batchRepo.recalculateSummary(
                                                companyId, branchId, importRecord.syncBatchId()));
                return processed;
        }

        /**
         * Executes the business validation phase for all eligible imports in a batch.
         * This transitions records from READY_FOR_VALIDATION to VALIDATED or FAILED.
         *
         * @param companyId the company ID
         * @param branchId  the branch ID
         * @param batchId   the batch ID
         * @return the number of records successfully validated
         */
        public int validateReadyImports(Long companyId, Long branchId, Long batchId) {
                batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                int validated = 0;
                while (validationProcessor.validateNextReadyImport(companyId, branchId, batchId)) {
                        validated++;
                }
                batchRepo.recalculateSummary(companyId, branchId, batchId);
                return validated;
        }

        /**
         * Validates a specific import record through the business validation phase.
         *
         * @param companyId            the company ID
         * @param branchId             the branch ID
         * @param offlineOrderImportId the ID of the import record
         * @return true if the record was validated
         */
        public boolean validateSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
                boolean validated = validationProcessor.validateSingleImport(companyId, branchId, offlineOrderImportId);
                importRepo.findByImportId(companyId, branchId, offlineOrderImportId)
                                .ifPresent(importRecord -> batchRepo.recalculateSummary(
                                                companyId, branchId, importRecord.syncBatchId()));
                return validated;
        }

        /**
         * Executes the final posting phase for all validated imports in a batch.
         * This transitions records from VALIDATED to SYNCED and creates the official POS orders.
         *
         * @param companyId the company ID
         * @param branchId  the branch ID
         * @param batchId   the batch ID
         * @return the number of records successfully posted
         */
        public int postValidatedImports(Long companyId, Long branchId, Long batchId) {
                batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                int posted = 0;
                while (postingProcessor.postNextValidatedImport(companyId, branchId, batchId)) {
                        posted++;
                }
                batchRepo.recalculateSummary(companyId, branchId, batchId);
                return posted;
        }

        /**
         * Posts a specific validated import record to create an official POS order.
         *
         * @param companyId            the company ID
         * @param branchId             the branch ID
         * @param offlineOrderImportId the ID of the import record
         * @return true if the record was posted
         */
        public boolean postSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
                boolean posted = postingProcessor.postSingleImport(companyId, branchId, offlineOrderImportId);
                importRepo.findByImportId(companyId, branchId, offlineOrderImportId)
                                .ifPresent(importRecord -> batchRepo.recalculateSummary(
                                                companyId, branchId, importRecord.syncBatchId()));
                return posted;
        }

        /**
         * Recovers imports that have been stuck in an intermediate status for too long.
         * Records in PROCESSING or VALIDATING are marked as FAILED, while those in POSTING
         * are moved to NEEDS_REVIEW for manual intervention.
         *
         * @param companyId        the company ID
         * @param branchId         the branch ID
         * @param batchId          the batch ID
         * @param thresholdMinutes the number of minutes to consider a record as stuck
         * @return the total number of recovered records
         */
        public int recoverStuckImports(Long companyId, Long branchId, Long batchId, int thresholdMinutes) {
                batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                int processing = importRepo.markStuckProcessingFailed(companyId, branchId, batchId, thresholdMinutes);
                int validating = importRepo.markStuckValidatingFailed(companyId, branchId, batchId, thresholdMinutes);
                int posting = importRepo.markStuckPostingNeedsReview(companyId, branchId, batchId, thresholdMinutes);
                batchRepo.recalculateSummary(companyId, branchId, batchId);
                auditLogService.logSyncEvent(
                                companyId, branchId, batchId, null, null, null,
                                "OFFLINE_STUCK_IMPORT_RECOVERY",
                                "Recovered stuck imports: processing=" + processing
                                                + ", validating=" + validating
                                                + ", postingNeedsReview=" + posting,
                                null);
                return processing + validating + posting;
        }

        /**
         * Forces a recalculation of the batch summary totals (synced, failed, etc.).
         *
         * @param companyId the company ID
         * @param branchId  the branch ID
         * @param batchId   the batch ID
         */
        public void recalculateBatchSummary(Long companyId, Long branchId, Long batchId) {
                batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                batchRepo.recalculateSummary(companyId, branchId, batchId);
        }

        /**
         * Retrieves the current status and summary statistics of a sync batch.
         *
         * @param companyId     the company ID
         * @param branchId      the branch ID
         * @param batchId       the batch ID
         * @param principalName the name of the requesting user
         * @return the sync status response
         */
        public SyncStatusResponse getSyncStatus(Long companyId, Long branchId, Long batchId, String principalName) {
                PosSyncBatchModel batch = batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                auditLogService.logSyncEvent(
                                companyId, branchId,
                                batchId, null, batch.deviceId(), batch.cashierId(),
                                "SYNC_STATUS_VIEWED",
                                "Sync status viewed by " + principalName,
                                null);

                return new SyncStatusResponse(
                                batch.id(), batch.clientBatchId(), batch.status(),
                                batch.totalOrders(), batch.syncedOrders(),
                                batch.failedOrders(), batch.duplicateOrders(),
                                batch.needsReviewOrders(),
                                batch.createdAt(), batch.syncCompletedAt());
        }

        /**
         * Retrieves a paginated list of errors for a sync batch.
         *
         * @param companyId     the company ID
         * @param branchId      the branch ID
         * @param batchId       the batch ID
         * @param cursor        the pagination cursor (error ID)
         * @param size          the page size
         * @param principalName the name of the requesting user
         * @return the paginated error list response
         */
        public SyncErrorListResponse getSyncErrors(Long companyId, Long branchId, Long batchId,
                        String cursor, Integer size, String principalName) {
                PosSyncBatchModel batch = batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                int pageSize = normalizePageSize(size);
                long afterErrorId = parseCursor(cursor);
                auditLogService.logSyncEvent(
                                companyId, branchId,
                                batchId, null, batch.deviceId(), batch.cashierId(),
                                "SYNC_ERRORS_VIEWED",
                                "Sync errors viewed by " + principalName,
                                null);

                return syncErrorService.getErrorsByBatchId(companyId, branchId, batchId, afterErrorId, pageSize);
        }

        /**
         * Requests a retry for a failed offline order import.
         * This verifies idempotency and marks the record as PENDING_RETRY.
         *
         * @param companyId            the company ID
         * @param branchId             the branch ID
         * @param offlineOrderImportId the ID of the import record
         * @param principalName        the name of the requesting user
         * @return the result of the retry request
         */
        public OfflineRetryResultResponse retryOfflineOrder(Long companyId, Long branchId, Long offlineOrderImportId,
                        String principalName) {
                OfflineOrderImportModel importRecord = importRepo.findByImportId(companyId, branchId, offlineOrderImportId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                 "IMPORT_NOT_FOUND",
                                                 "Offline order import not found: " + offlineOrderImportId));

                log.info("Retry requested for offlineOrderImportId={}, current status={}",
                                offlineOrderImportId, importRecord.status());
                auditLogService.logSyncEvent(
                                companyId, branchId,
                                importRecord.syncBatchId(), importRecord.id(), importRecord.deviceId(),
                                importRecord.cashierId(),
                                "OFFLINE_ORDER_RETRY_REQUESTED",
                                "Offline order retry requested by " + principalName,
                                null);

                if (importRecord.status() != OfflineOrderImportStatus.FAILED
                                && importRecord.status() != OfflineOrderImportStatus.NEEDS_REVIEW) {
                        auditLogService.logSyncEvent(
                                        companyId, branchId,
                                        importRecord.syncBatchId(), importRecord.id(), importRecord.deviceId(),
                                        importRecord.cashierId(),
                                        "OFFLINE_ORDER_RETRY_REJECTED",
                                        "Retry rejected for status " + importRecord.status() + " by " + principalName,
                                        null);
                        throw new OfflineSyncException(
                                        "OFFLINE_ORDER_RETRY_NOT_ALLOWED",
                                        "Retry is allowed only for FAILED or NEEDS_REVIEW imports");
                }

                idempotencyService.requireMatchingRecord(
                                companyId,
                                branchId,
                                importRecord.deviceId(),
                                importRecord.idempotencyKey(),
                                importRecord.payloadHash());

                int updated = importRepo.markPendingRetry(companyId, branchId, offlineOrderImportId);
                if (updated == 0) {
                        auditLogService.logSyncEvent(
                                        companyId, branchId,
                                        importRecord.syncBatchId(), importRecord.id(), importRecord.deviceId(),
                                        importRecord.cashierId(),
                                        "OFFLINE_ORDER_RETRY_REJECTED",
                                        "Retry rejected because import status changed before update",
                                        null);
                        throw new OfflineSyncException(
                                        "OFFLINE_ORDER_RETRY_STATE_CHANGED",
                                        "Offline order import status changed before retry could be accepted");
                }

                Instant retryAt = Instant.now();
                auditLogService.logSyncEvent(
                                companyId, branchId,
                                importRecord.syncBatchId(), importRecord.id(), importRecord.deviceId(),
                                importRecord.cashierId(),
                                "OFFLINE_ORDER_RETRY_ACCEPTED",
                                "Retry accepted by " + principalName + "; import marked PENDING_RETRY",
                                null);
                batchRepo.recalculateSummary(companyId, branchId, importRecord.syncBatchId());

                return new OfflineRetryResultResponse(
                                importRecord.id(),
                                importRecord.offlineOrderNo(),
                                importRecord.idempotencyKey(),
                                importRecord.status(),
                                OfflineOrderImportStatus.PENDING_RETRY,
                                importRecord.retryCount() + 1,
                                retryAt,
                                true,
                                "Retry accepted. Import marked PENDING_RETRY; posting is not executed yet.");
        }

        /**
         * Stores an individual offline order as an import record.
         * Verifies idempotency before insertion.
         *
         * @param batchId the internal batch ID
         * @param batch   the original upload request
         * @param order   the specific order to store
         * @return the result of storing the order
         */
        private OfflineOrderSyncResult storeOfflineOrder(Long batchId,
                        OfflineSyncUploadRequest batch,
                        OfflineOrderRequest order) {
                try {
                        String payloadJson = gson.toJson(order);
                        String payloadHash = sha256(payloadJson);

                        IdempotencyClaimResult claim = idempotencyService.claimIdempotencyKey(
                                        batch.companyId(), batch.branchId(), batch.deviceId(),
                                        order.idempotencyKey(), order.offlineOrderNo(), payloadHash);

                        if (!claim.payloadMatches()) {
                                log.warn("Idempotency payload mismatch detected: companyId={}, branchId={}, deviceId={}, key={}",
                                                batch.companyId(), batch.branchId(), batch.deviceId(), order.idempotencyKey());
                                return new OfflineOrderSyncResult(
                                                order.offlineOrderNo(), order.idempotencyKey(),
                                                OfflineOrderImportStatus.FAILED,
                                                null, null,
                                                "IDEMPOTENCY_PAYLOAD_MISMATCH",
                                                "Same idempotency key was used with a different payload",
                                                Collections.emptyList());
                        }

                        if (!claim.newlyClaimed()) {
                                return existingIdempotentResult(batch, order);
                        }

                        Long importId;
                        try {
                                importId = importRepo.insertImport(
                                                batchId, batch.companyId(), batch.branchId(),
                                                batch.deviceId(), batch.cashierId(),
                                                order.offlineOrderNo(), order.idempotencyKey(),
                                                order.localOrderCreatedAt(),
                                                payloadJson, payloadHash);
                        } catch (DuplicateKeyException duplicate) {
                                return existingIdempotentResult(batch, order);
                        }

                        log.debug("Stored offline order import: id={}, offlineOrderNo={}",
                                        importId, order.offlineOrderNo());

                        return new OfflineOrderSyncResult(
                                        order.offlineOrderNo(), order.idempotencyKey(),
                                        OfflineOrderImportStatus.PENDING,
                                        null, null, null, null,
                                        Collections.emptyList());

                } catch (Exception ex) {
                        log.error("Failed to store offline order: offlineOrderNo={}",
                                        order.offlineOrderNo(), ex);
                        return new OfflineOrderSyncResult(
                                        order.offlineOrderNo(), order.idempotencyKey(),
                                        OfflineOrderImportStatus.FAILED,
                                        null, null,
                                        "INTERNAL_PROCESSING_ERROR",
                                        "Failed to store order: " + ex.getMessage(),
                                        Collections.emptyList());
                }
        }

        /**
         * Resolves the result for an idempotent replay (duplicate request).
         *
         * @param batch the original upload request
         * @param order the specific order request
         * @return the result response based on the existing record's state
         */
        private OfflineOrderSyncResult existingIdempotentResult(OfflineSyncUploadRequest batch, OfflineOrderRequest order) {
                return importRepo.findByIdempotencyKey(
                                batch.companyId(), batch.branchId(), batch.deviceId(), order.idempotencyKey())
                                .map(existing -> new OfflineOrderSyncResult(
                                                existing.offlineOrderNo(),
                                                existing.idempotencyKey(),
                                                existing.status(),
                                                existing.officialOrderId(),
                                                existing.officialInvoiceNo(),
                                                existing.errorCode(),
                                                existing.errorMessage(),
                                                List.of("IDEMPOTENT_REPLAY")))
                                .orElseGet(() -> new OfflineOrderSyncResult(
                                                order.offlineOrderNo(),
                                                order.idempotencyKey(),
                                                OfflineOrderImportStatus.PENDING,
                                                null,
                                                null,
                                                null,
                                                "Idempotency key already claimed; import is not yet visible",
                                                List.of("IDEMPOTENT_CLAIM_IN_PROGRESS")));
        }

        /**
         * Generates a SHA-256 hash of a string.
         *
         * @param input the string to hash
         * @return the hex-encoded hash
         */
        private String sha256(String input) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                        return HexFormat.of().formatHex(hash);
                } catch (Exception e) {
                        log.warn("SHA-256 hashing failed, using fallback", e);
                        return String.valueOf(input.hashCode());
                }
        }

        /**
         * Normalizes the page size for error retrieval based on configuration limits.
         *
         * @param size the requested size
         * @return the normalized size
         */
        private int normalizePageSize(Integer size) {
                int requested = size != null ? size : props.getMaxBootstrapPageSize();
                return Math.max(1, Math.min(requested, props.getMaxBootstrapPageSize()));
        }

        /**
         * Parses a pagination cursor string into a long.
         *
         * @param cursor the cursor string
         * @return the parsed long value
         * @throws OfflineSyncException if the cursor is invalid
         */
        private long parseCursor(String cursor) {
                if (cursor == null || cursor.isBlank()) {
                        return 0L;
                }
                try {
                        long parsed = Long.parseLong(cursor);
                        if (parsed < 0) {
                                throw new NumberFormatException("cursor must be non-negative");
                        }
                        return parsed;
                } catch (NumberFormatException ex) {
                        throw new OfflineSyncException("INVALID_SYNC_ERROR_CURSOR", "Invalid sync error cursor");
                }
        }
}
