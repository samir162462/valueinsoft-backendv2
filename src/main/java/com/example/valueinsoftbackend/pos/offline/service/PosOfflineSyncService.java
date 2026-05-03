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
        private final OfflinePosProperties props;
        private final Gson gson = new Gson();

        public PosOfflineSyncService(PosSyncBatchRepository batchRepo,
                        OfflineOrderImportRepository importRepo,
                        PosDeviceService deviceService,
                        OfflineOrderValidationService validationService,
                        PosIdempotencyService idempotencyService,
                        SyncErrorService syncErrorService,
                        AuditLogService auditLogService,
                        OfflineSingleOrderProcessor singleOrderProcessor,
                        OfflineOrderValidationProcessor validationProcessor,
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
                this.props = props;
        }

        /**
         * Receives an offline sync upload, validates basic fields,
         * stores each order as a raw import record, and returns a
         * RECEIVED status. Processing is handled through separate per-import boundaries.
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
         * Internal Phase 6 processing skeleton. This deliberately does not run
         * invoice, payment, inventory, or finance posting.
         */
        public int processPendingImports(Long companyId, Long branchId, Long batchId) {
                batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                int processed = 0;
                while (singleOrderProcessor.processNextPendingImport(companyId, branchId, batchId)) {
                        processed++;
                }
                return processed;
        }

        /**
         * Internal Phase 6 single-import boundary. The processor claims only
         * PENDING/PENDING_RETRY imports and skips all other statuses.
         */
        public boolean processSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
                return singleOrderProcessor.processSingleImport(companyId, branchId, offlineOrderImportId);
        }

        /**
         * Internal Phase 7 validation loop. Validates one import per transaction
         * through OfflineOrderValidationProcessor and does not post final business rows.
         */
        public int validateReadyImports(Long companyId, Long branchId, Long batchId) {
                batchRepo.findById(companyId, branchId, batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));
                int validated = 0;
                while (validationProcessor.validateNextReadyImport(companyId, branchId, batchId)) {
                        validated++;
                }
                return validated;
        }

        /**
         * Internal Phase 7 single-import validation boundary.
         */
        public boolean validateSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
                return validationProcessor.validateSingleImport(companyId, branchId, offlineOrderImportId);
        }

        /**
         * Query current status of a sync batch.
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
         * Get errors for all orders in a sync batch.
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
         * Retry processing a single failed offline order import.
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

        // -------------------------------------------------------
        // Private helpers
        // -------------------------------------------------------

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

        private int normalizePageSize(Integer size) {
                int requested = size != null ? size : props.getMaxBootstrapPageSize();
                return Math.max(1, Math.min(requested, props.getMaxBootstrapPageSize()));
        }

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
