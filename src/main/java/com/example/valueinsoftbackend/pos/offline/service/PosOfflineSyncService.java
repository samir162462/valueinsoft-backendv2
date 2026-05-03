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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        private final AuditLogService auditLogService;
        private final OfflinePosProperties props;
        private final Gson gson = new Gson();

        public PosOfflineSyncService(PosSyncBatchRepository batchRepo,
                        OfflineOrderImportRepository importRepo,
                        PosDeviceService deviceService,
                        OfflineOrderValidationService validationService,
                        PosIdempotencyService idempotencyService,
                        AuditLogService auditLogService,
                        OfflinePosProperties props) {
                this.batchRepo = batchRepo;
                this.importRepo = importRepo;
                this.deviceService = deviceService;
                this.validationService = validationService;
                this.idempotencyService = idempotencyService;
                this.auditLogService = auditLogService;
                this.props = props;
        }

        /**
         * Receives an offline sync upload, validates basic fields,
         * stores each order as a raw import record, and returns a
         * RECEIVED status. Actual processing happens in Phase 2.
         */
        public OfflineSyncUploadResponse uploadOfflineSync(OfflineSyncUploadRequest request) {
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
                                "Received batch with " + request.orders().size() + " orders",
                                null);

                // TODO: In Phase 2, trigger async processing of stored orders:
                // - For each PENDING order import:
                // a) Check idempotency
                // b) Validate order details (products, prices, stock, taxes)
                // c) Create official invoice via InvoiceCreationService
                // d) Create payments via PaymentCreationService
                // e) Post inventory via InventoryMovementService
                // f) Post finance journal via FinancePostingService
                // g) Update import status to SYNCED or FAILED
                // - Update batch summary counts

                return new OfflineSyncUploadResponse(
                                batchId, request.clientBatchId(),
                                PosSyncBatchStatus.RECEIVED,
                                request.orders().size(),
                                0, 0, 0, 0,
                                results);
        }

        /**
         * Query current status of a sync batch.
         */
        public SyncStatusResponse getSyncStatus(Long batchId) {
                PosSyncBatchModel batch = batchRepo.findById(batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));

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
        public List<SyncErrorResponse> getSyncErrors(Long batchId) {
                // Verify batch exists
                batchRepo.findById(batchId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                "BATCH_NOT_FOUND", "Sync batch not found: " + batchId));

                // TODO: Delegate to SyncErrorService once error recording is implemented
                return Collections.emptyList();
        }

        /**
         * Retry processing a single failed offline order import.
         */
        public OfflineOrderSyncResult retryOfflineOrder(Long offlineOrderImportId) {
                OfflineOrderImportModel importRecord = importRepo.findById(offlineOrderImportId)
                                .orElseThrow(() -> new OfflineSyncException(
                                                "IMPORT_NOT_FOUND",
                                                "Offline order import not found: " + offlineOrderImportId));

                // TODO: In Phase 2, re-run the processing pipeline for this single order:
                // 1. Re-parse payloadJson
                // 2. Re-validate
                // 3. Attempt invoice creation
                // 4. Update status

                log.info("Retry requested for offlineOrderImportId={}, current status={}",
                                offlineOrderImportId, importRecord.status());

                return new OfflineOrderSyncResult(
                                importRecord.offlineOrderNo(),
                                importRecord.idempotencyKey(),
                                importRecord.status(),
                                importRecord.officialOrderId(),
                                importRecord.officialInvoiceNo(),
                                importRecord.errorCode(),
                                importRecord.errorMessage(),
                                Collections.emptyList());
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

                        // Check idempotency before storing
                        boolean isDuplicate = idempotencyService.existsByKey(
                                        batch.companyId(), batch.branchId(),
                                        batch.deviceId(), order.idempotencyKey());

                        if (isDuplicate) {
                                log.info("Duplicate idempotency key detected: {}", order.idempotencyKey());
                                return new OfflineOrderSyncResult(
                                                order.offlineOrderNo(), order.idempotencyKey(),
                                                OfflineOrderImportStatus.DUPLICATE,
                                                null, null,
                                                "DUPLICATE_IDEMPOTENCY_KEY",
                                                "Order already processed with this idempotency key",
                                                Collections.emptyList());
                        }

                        Long importId = importRepo.insertImport(
                                        batchId, batch.companyId(), batch.branchId(),
                                        batch.deviceId(), batch.cashierId(),
                                        order.offlineOrderNo(), order.idempotencyKey(),
                                        order.localOrderCreatedAt(),
                                        payloadJson, payloadHash);

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
}
