package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Service.PosSalePostingService;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.model.PosIdempotencyModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Processor responsible for converting validated offline order imports into official POS orders.
 * It handles claiming eligible records, mapping the offline payload to the online order model,
 * and performing the actual posting via {@link PosSalePostingService}.
 */
@Service
@Slf4j
public class OfflineOrderPostingProcessor {

    private static final String FINANCE_STATUS_ENQUEUED = "ENQUEUED";
    private static final String FINANCE_STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String FINANCE_STATUS_ENQUEUE_FAILED = "ENQUEUE_FAILED";
    private static final int COMPACT_ERROR_LENGTH = 300;

    private final OfflineOrderImportRepository importRepo;
    private final DbPosShiftPeriod dbPosShiftPeriod;
    private final PosIdempotencyService idempotencyService;
    private final PosSalePostingService posSalePostingService;
    private final SyncErrorService syncErrorService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final Gson gson = new Gson();

    /**
     * Constructs a new OfflineOrderPostingProcessor with required dependencies.
     *
     * @param importRepo           the repository for offline order imports
     * @param idempotencyService   the service for handling idempotency
     * @param posSalePostingService the service for posting official POS sales
     * @param syncErrorService     the service for logging synchronization errors
     * @param auditLogService      the service for logging audit events
     * @param transactionTemplate  the template for transactional execution
     */
    public OfflineOrderPostingProcessor(OfflineOrderImportRepository importRepo,
                                        DbPosShiftPeriod dbPosShiftPeriod,
                                        PosIdempotencyService idempotencyService,
                                        PosSalePostingService posSalePostingService,
                                        SyncErrorService syncErrorService,
                                        AuditLogService auditLogService,
                                        TransactionTemplate transactionTemplate) {
        this.importRepo = importRepo;
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.idempotencyService = idempotencyService;
        this.posSalePostingService = posSalePostingService;
        this.syncErrorService = syncErrorService;
        this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Finds and posts the next available validated import for a batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID
     * @return true if an import was found and processed, false otherwise
     */
    public boolean postNextValidatedImport(Long companyId, Long branchId, Long batchId) {
        Optional<OfflineOrderImportModel> claimed = transactionTemplate.execute(status ->
                importRepo.claimNextValidatedForPosting(companyId, branchId, batchId));
        if (claimed == null || claimed.isEmpty()) {
            auditLogService.logSyncEvent(
                    companyId, branchId, batchId, null, null, null,
                    "OFFLINE_POSTING_SKIPPED",
                    "No VALIDATED import was available to claim for posting",
                    null);
            return false;
        }
        postClaimedImport(claimed.get());
        return true;
    }

    /**
     * Posts a specific offline order import.
     *
     * @param companyId            the company ID
     * @param branchId             the branch ID
     * @param offlineOrderImportId the ID of the import to post
     * @return true if the import was successfully claimed and processed
     */
    public boolean postSingleImport(Long companyId, Long branchId, Long offlineOrderImportId) {
        Optional<OfflineOrderImportModel> claimed = transactionTemplate.execute(status ->
                importRepo.claimImportForPosting(companyId, branchId, offlineOrderImportId));
        if (claimed == null || claimed.isEmpty()) {
            auditLogService.logSyncEvent(
                    companyId, branchId, null, offlineOrderImportId, null, null,
                    "OFFLINE_POSTING_SKIPPED",
                    "Import was already claimed or is not eligible for posting",
                    null);
            return false;
        }
        postClaimedImport(claimed.get());
        return true;
    }

    /**
     * Executes the posting logic for a claimed import record.
     *
     * @param importRecord the claimed import record
     */
    private void postClaimedImport(OfflineOrderImportModel importRecord) {
        auditLogService.logSyncEvent(
                importRecord.companyId(), importRecord.branchId(), importRecord.syncBatchId(), importRecord.id(),
                importRecord.deviceId(), importRecord.cashierId(),
                "OFFLINE_POSTING_STARTED",
                "Offline import claimed for MVP posting",
                null);

        try {
            transactionTemplate.executeWithoutResult(status -> postWithinTransaction(importRecord));
        } catch (OfflineSyncException ex) {
            markPostingFailed(importRecord, ex.getErrorCode(), ex.getMessage(), severity(ex.getErrorCode()));
        } catch (Exception ex) {
            log.error("Unexpected offline posting failure: importId={}", importRecord.id(), ex);
            markPostingFailed(importRecord, "OFFLINE_POSTING_FAILED",
                    "Offline posting failed: " + ex.getMessage(), OfflineErrorSeverity.SYSTEM_ERROR);
        }
    }

    /**
     * Performs the actual posting logic within a database transaction.
     *
     * @param importRecord the import record to post
     * @throws OfflineSyncException if validation or posting fails
     */
    private void postWithinTransaction(OfflineOrderImportModel importRecord) {
        PosIdempotencyModel idempotency = idempotencyService.requireMatchingRecord(
                importRecord.companyId(),
                importRecord.branchId(),
                importRecord.deviceId(),
                importRecord.idempotencyKey(),
                importRecord.payloadHash());

        if (importRecord.officialOrderId() != null || idempotency.officialOrderId() != null) {
            auditLogService.logSyncEvent(
                    importRecord.companyId(), importRecord.branchId(), importRecord.syncBatchId(), importRecord.id(),
                    importRecord.deviceId(), importRecord.cashierId(),
                    "OFFLINE_DUPLICATE_POSTING_BLOCKED",
                    "Posting blocked because import/idempotency already has posted order metadata",
                    null);
            throw new OfflineSyncException(
                    "OFFLINE_DUPLICATE_POSTING_ATTEMPT",
                    "Offline import already has posted order metadata");
        }

        Order order = mapToOnlineOrder(importRecord);
        com.example.valueinsoftbackend.Model.Response.CreateOrderResult posted = posSalePostingService.postSale(
                importRecord.companyId().intValue(),
                order,
                (postedResult, financeRequest) -> handleFinanceEnqueued(importRecord, postedResult, financeRequest),
                (postedResult, exception) -> handleFinanceEnqueueFailed(importRecord, postedResult, exception));

        Long postedOrderId = (long) posted.orderId();
        importRepo.markPostingSynced(
                importRecord.companyId(),
                importRecord.branchId(),
                importRecord.id(),
                postedOrderId,
                FINANCE_STATUS_UNAVAILABLE,
                null);
        idempotencyService.markSynced(
                importRecord.companyId(),
                importRecord.branchId(),
                importRecord.deviceId(),
                importRecord.idempotencyKey(),
                postedOrderId,
                null,
                resultMetadata(postedOrderId, posted.shiftId(), null, FINANCE_STATUS_UNAVAILABLE, null));

        auditLogService.logSyncEvent(
                importRecord.companyId(), importRecord.branchId(), importRecord.syncBatchId(), importRecord.id(),
                importRecord.deviceId(), importRecord.cashierId(),
                "OFFLINE_POSTING_SUCCEEDED",
                "Offline import posted as POS order " + postedOrderId,
                resultMetadata(postedOrderId, posted.shiftId(), null, FINANCE_STATUS_UNAVAILABLE, null));
    }

    private void handleFinanceEnqueued(OfflineOrderImportModel importRecord,
                                       com.example.valueinsoftbackend.Model.Response.CreateOrderResult postedResult,
                                       Optional<FinancePostingRequestItem> financeRequest) {
        Long postedOrderId = (long) postedResult.orderId();
        UUID requestId = financeRequest
                .map(FinancePostingRequestItem::getPostingRequestId)
                .orElse(null);
        String financeStatus = requestId == null ? FINANCE_STATUS_UNAVAILABLE : FINANCE_STATUS_ENQUEUED;
        String metadataJson = resultMetadata(postedOrderId, postedResult.shiftId(), requestId, financeStatus, null);
        try {
            importRepo.updateFinanceEnqueueMetadata(
                    importRecord.companyId(),
                    importRecord.branchId(),
                    importRecord.id(),
                    requestId,
                    financeStatus,
                    null);
            idempotencyService.updateResultMetadata(
                    importRecord.companyId(),
                    importRecord.branchId(),
                    importRecord.deviceId(),
                    importRecord.idempotencyKey(),
                    metadataJson);
            auditLogService.logSyncEvent(
                    importRecord.companyId(), importRecord.branchId(), importRecord.syncBatchId(), importRecord.id(),
                    importRecord.deviceId(), importRecord.cashierId(),
                    requestId == null ? "OFFLINE_FINANCE_ENQUEUE_UNAVAILABLE" : "OFFLINE_FINANCE_ENQUEUE_CAPTURED",
                    requestId == null
                            ? "POS order posted without a finance posting request id"
                            : "Captured finance posting request for offline POS order",
                    metadataJson);
        } catch (RuntimeException ex) {
            log.warn("Failed to capture finance enqueue metadata for offline import {}: {}",
                    importRecord.id(), ex.getMessage());
        }
    }

    private void handleFinanceEnqueueFailed(OfflineOrderImportModel importRecord,
                                            com.example.valueinsoftbackend.Model.Response.CreateOrderResult postedResult,
                                            RuntimeException exception) {
        Long postedOrderId = (long) postedResult.orderId();
        String compactError = compact(exception.getMessage());
        String metadataJson = resultMetadata(postedOrderId, postedResult.shiftId(), null, FINANCE_STATUS_ENQUEUE_FAILED, compactError);
        try {
            importRepo.updateFinanceEnqueueMetadata(
                    importRecord.companyId(),
                    importRecord.branchId(),
                    importRecord.id(),
                    null,
                    FINANCE_STATUS_ENQUEUE_FAILED,
                    compactError);
            idempotencyService.updateResultMetadata(
                    importRecord.companyId(),
                    importRecord.branchId(),
                    importRecord.deviceId(),
                    importRecord.idempotencyKey(),
                    metadataJson);
            syncErrorService.saveError(
                    importRecord.id(),
                    importRecord.companyId(),
                    importRecord.branchId(),
                    "FINANCE_ENQUEUE",
                    "OFFLINE_FINANCE_ENQUEUE_FAILED",
                    compactError,
                    null,
                    null,
                    OfflineErrorSeverity.WARNING,
                    true,
                    false);
            auditLogService.logSyncEvent(
                    importRecord.companyId(), importRecord.branchId(), importRecord.syncBatchId(), importRecord.id(),
                    importRecord.deviceId(), importRecord.cashierId(),
                    "OFFLINE_FINANCE_ENQUEUE_FAILED",
                    "Offline POS order was synced but finance enqueue failed",
                    metadataJson);
        } catch (RuntimeException ex) {
            log.warn("Failed to record finance enqueue failure metadata for offline import {}: {}",
                    importRecord.id(), ex.getMessage());
        }
    }

    private String resultMetadata(Long postedOrderId, Integer shiftId, UUID financePostingRequestId,
                                  String financeStatus, String financeError) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("postedOrderId", postedOrderId);
        metadata.put("officialOrderId", postedOrderId);
        if (shiftId != null) {
            metadata.put("shiftId", shiftId);
        }
        if (financePostingRequestId != null) {
            metadata.put("financePostingRequestId", financePostingRequestId.toString());
        }
        metadata.put("financeStatus", financeStatus);
        if (financeError != null && !financeError.isBlank()) {
            metadata.put("financeError", compact(financeError));
        }
        return gson.toJson(metadata);
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "Finance enqueue failed";
        }
        String trimmed = value.trim();
        return trimmed.length() <= COMPACT_ERROR_LENGTH
                ? trimmed
                : trimmed.substring(0, COMPACT_ERROR_LENGTH);
    }

    /**
     * Maps the offline JSON payload to the official {@link Order} model.
     * Note: This currently only supports single-tender payments as per MVP requirements.
     *
     * @param importRecord the import record containing the payload
     * @return the mapped Order object
     * @throws OfflineSyncException if mapping fails or unsupported features are used
     */
    private Order mapToOnlineOrder(OfflineOrderImportModel importRecord) {
        JsonObject payload = parsePayload(importRecord.payloadJson());
        JsonArray items = array(payload, "items");
        if (items == null || items.isEmpty()) {
            throw new OfflineSyncException("OFFLINE_ORDER_EMPTY_LINES", "Order must contain at least one line");
        }
        JsonArray payments = array(payload, "payments");
        if (payments != null && payments.size() > 1) {
            throw new OfflineSyncException(
                    "OFFLINE_MULTI_TENDER_NOT_SUPPORTED",
                    "Offline posting MVP supports one payment tender only");
        }

        ArrayList<OrderDetails> details = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = object(items.get(i));
            if (item == null) {
                throw new OfflineSyncException("OFFLINE_ORDER_PAYLOAD_INVALID", "Line item must be an object");
            }
            Long productId = positiveLong(item, "productId");
            int quantity = integerAmount(item, "quantity", "OFFLINE_DECIMAL_QUANTITY_NOT_SUPPORTED");
            int unitPrice = integerAmount(item, "unitPrice", "OFFLINE_DECIMAL_AMOUNT_NOT_SUPPORTED");
            int lineTotal = integerAmount(item, "lineTotal", "OFFLINE_DECIMAL_AMOUNT_NOT_SUPPORTED");
            String itemName = text(item, "productSnapshotName", "Product " + productId);
            details.add(new OrderDetails(
                    0,
                    productId.intValue(),
                    itemName,
                    quantity,
                    unitPrice,
                    lineTotal,
                    productId.intValue(),
                    0));
        }

        int orderDiscount = integerAmount(payload, "discountAmount", "OFFLINE_DECIMAL_AMOUNT_NOT_SUPPORTED", 0);
        int orderTotal = integerAmount(payload, "totalAmount", "OFFLINE_DECIMAL_AMOUNT_NOT_SUPPORTED");
        int clientId = optionalPositiveInt(payload, "customerId");
        String clientName = localCustomerName(payload);
        String orderType = paymentMethod(payload, payments);
        String salesUser = "offline-cashier-" + importRecord.cashierId();

        Integer requestedShiftId = resolvePostingShiftId(importRecord, payload);

        Order order = new Order(
                0,
                new Timestamp(System.currentTimeMillis()),
                clientName,
                orderType,
                orderDiscount,
                orderTotal,
                salesUser,
                importRecord.branchId().intValue(),
                clientId,
                orderTotal,
                0,
                details);
        order.setRequestedShiftId(requestedShiftId);
        return order;
    }

    private Integer resolvePostingShiftId(OfflineOrderImportModel importRecord, JsonObject payload) {
        Integer preferredShiftId = optionalPositiveInteger(text(payload, "localShiftId", null));
        Optional<Integer> resolved = dbPosShiftPeriod.findOpenShiftIdForPosting(
                importRecord.companyId().intValue(),
                importRecord.branchId().intValue(),
                preferredShiftId);

        if (resolved.isPresent()) {
            return resolved.get();
        }

        if (preferredShiftId != null) {
            throw new OfflineSyncException(
                    "OFFLINE_SHIFT_NOT_OPEN",
                    "Offline order references shift " + preferredShiftId + ", but that shift is not open for this branch");
        }

        throw new OfflineSyncException(
                "OFFLINE_SHIFT_REQUIRED",
                "Offline order cannot be posted because no open POS shift exists for this branch");
    }

    /**
     * Parses the payload JSON.
     *
     * @param payloadJson the raw JSON
     * @return the parsed JsonObject
     */
    private JsonObject parsePayload(String payloadJson) {
        try {
            JsonElement element = JsonParser.parseString(payloadJson);
            if (element == null || !element.isJsonObject()) {
                throw new IllegalArgumentException("payload must be an object");
            }
            return element.getAsJsonObject();
        } catch (Exception ex) {
            throw new OfflineSyncException("OFFLINE_ORDER_PAYLOAD_INVALID", "Order payload is not readable JSON");
        }
    }

    /**
     * Safely gets a JsonArray from a JsonObject.
     *
     * @param object the object
     * @param field  the field name
     * @return the JsonArray, or null if missing
     */
    private JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    /**
     * Safely converts a JsonElement to a JsonObject.
     *
     * @param element the element
     * @return the object, or null
     */
    private JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /**
     * Safely extracts a positive Long from a JsonObject.
     *
     * @param object the object
     * @param field  the field name
     * @return the Long value
     * @throws OfflineSyncException if invalid
     */
    private Long positiveLong(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new OfflineSyncException("OFFLINE_ORDER_PAYLOAD_INVALID", field + " is required");
        }
        try {
            long parsed = value.getAsLong();
            if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
                throw new NumberFormatException("not a supported positive int");
            }
            return parsed;
        } catch (Exception ex) {
            throw new OfflineSyncException("OFFLINE_ORDER_PAYLOAD_INVALID", field + " must be a supported positive integer");
        }
    }

    /**
     * Safely extracts an optional positive int from a JsonObject.
     *
     * @param object the object
     * @param field  the field name
     * @return the int value, or 0 if missing
     */
    private int optionalPositiveInt(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return 0;
        }
        try {
            int parsed = value.getAsInt();
            return parsed > 0 ? parsed : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private Integer optionalPositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Safely extracts an integer amount from a JsonObject.
     *
     * @param object    the object
     * @param field     the field name
     * @param errorCode the error code if invalid
     * @return the int value
     */
    private int integerAmount(JsonObject object, String field, String errorCode) {
        return integerAmount(object, field, errorCode, null);
    }

    /**
     * Safely extracts an integer amount from a JsonObject with an optional default.
     *
     * @param object       the object
     * @param field        the field name
     * @param errorCode    the error code if invalid
     * @param defaultValue the default value
     * @return the int value
     */
    private int integerAmount(JsonObject object, String field, String errorCode, Integer defaultValue) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            if (defaultValue != null) {
                return defaultValue;
            }
            throw new OfflineSyncException("OFFLINE_ORDER_PAYLOAD_INVALID", field + " is required");
        }
        try {
            BigDecimal amount = value.getAsBigDecimal();
            BigDecimal normalized = amount.stripTrailingZeros();
            if (normalized.scale() > 0) {
                throw new OfflineSyncException(errorCode, field + " must be an integer-compatible value");
            }
            return normalized.intValueExact();
        } catch (OfflineSyncException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OfflineSyncException(errorCode, field + " must be an integer-compatible value");
        }
    }

    /**
     * Safely extracts text from a JsonObject.
     *
     * @param object       the object
     * @param field        the field name
     * @param defaultValue the default value
     * @return the trimmed string value
     */
    private String text(JsonObject object, String field, String defaultValue) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull() || value.getAsString().isBlank()) {
            return defaultValue;
        }
        return value.getAsString().trim();
    }

    /**
     * Resolves the local customer name from the payload.
     *
     * @param payload the order payload
     * @return the customer name
     */
    private String localCustomerName(JsonObject payload) {
        JsonObject localCustomer = object(payload.get("localCustomer"));
        if (localCustomer != null) {
            return text(localCustomer, "name", "");
        }
        return "";
    }

    /**
     * Resolves the payment method (order type) for the POS order.
     *
     * @param payload  the order payload
     * @param payments the payments array
     * @return the normalized payment method
     */
    private String paymentMethod(JsonObject payload, JsonArray payments) {
        if (payments != null && payments.size() == 1) {
            JsonObject payment = object(payments.get(0));
            if (payment != null) {
                return normalizePaymentMethod(text(payment, "paymentMethod", null));
            }
        }
        return normalizePaymentMethod(text(payload, "saleType", "Direct"));
    }

    /**
     * Normalizes payment method names to match system constants.
     *
     * @param value the raw payment method name
     * @return the normalized name
     */
    private String normalizePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return "Direct";
        }
        String normalized = value.trim();
        if ("cash".equalsIgnoreCase(normalized) || "direct".equalsIgnoreCase(normalized)
                || "dirict".equalsIgnoreCase(normalized)) {
            return "Direct";
        }
        return normalized;
    }

    /**
     * Marks an import as failed and logs the error details.
     *
     * @param importRecord the import record
     * @param errorCode    the error code
     * @param errorMessage the error message
     * @param severity     the error severity
     */
    private void markPostingFailed(OfflineOrderImportModel importRecord, String errorCode, String errorMessage,
                                   OfflineErrorSeverity severity) {
        transactionTemplate.executeWithoutResult(status -> {
            importRepo.markPostingFailed(
                    importRecord.companyId(), importRecord.branchId(), importRecord.id(), errorCode, errorMessage);
            syncErrorService.saveError(
                    importRecord.id(),
                    importRecord.companyId(),
                    importRecord.branchId(),
                    "POSTING",
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
                    "OFFLINE_POSTING_FAILED",
                    "Offline import posting failed: " + errorCode,
                    null);
        });
    }

    /**
     * Determines the error severity based on the error code.
     *
     * @param errorCode the error code
     * @return the severity level
     */
    private OfflineErrorSeverity severity(String errorCode) {
        if ("OFFLINE_POSTING_FAILED".equals(errorCode)) {
            return OfflineErrorSeverity.SYSTEM_ERROR;
        }
        return OfflineErrorSeverity.HARD_FAIL;
    }
}

