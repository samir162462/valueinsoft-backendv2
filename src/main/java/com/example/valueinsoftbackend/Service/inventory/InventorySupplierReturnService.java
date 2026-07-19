package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryAdjustmentRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventorySupplierReturnRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventorySupplierReturnRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventorySupplierReturnResponse;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InventorySupplierReturnService {

    private static final String OPERATION_TYPE = "INVENTORY_SUPPLIER_RETURN";

    private final DbInventoryAdjustmentRepository commandRepository;
    private final DbInventorySupplierReturnRepository supplierReturnRepository;
    private final DbInventoryStockMovementRepository movementRepository;
    private final FinanceOperationalPostingService financePostingService;
    private final ObjectMapper objectMapper;

    public InventorySupplierReturnService(DbInventoryAdjustmentRepository commandRepository,
                                          DbInventorySupplierReturnRepository supplierReturnRepository,
                                          DbInventoryStockMovementRepository movementRepository,
                                          FinanceOperationalPostingService financePostingService,
                                          ObjectMapper objectMapper) {
        this.commandRepository = commandRepository;
        this.supplierReturnRepository = supplierReturnRepository;
        this.movementRepository = movementRepository;
        this.financePostingService = financePostingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventorySupplierReturnResponse create(String actorName, InventorySupplierReturnRequest request) {
        validateEnvelope(request);
        String normalizedActor = normalizeActor(actorName);
        String requestHash = hashRequest(request);
        commandRepository.insertPendingIdempotency(
                request.companyId(), request.branchId(), OPERATION_TYPE, request.idempotencyKey().trim(),
                requestHash, normalizedActor, UUID.randomUUID().toString());

        DbInventoryAdjustmentRepository.IdempotencyRecord idempotency = commandRepository
                .findIdempotencyForUpdate(
                        request.companyId(), request.branchId(), OPERATION_TYPE, request.idempotencyKey().trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "IDEMPOTENCY_STATE_MISSING",
                        "Supplier return idempotency state could not be locked"));
        if (!requestHash.equals(idempotency.requestHash())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                    "The same idempotency key was already used with a different supplier return payload");
        }
        if ("COMPLETED".equalsIgnoreCase(idempotency.status()) && idempotency.responsePayload() != null) {
            return objectMapper.convertValue(
                    idempotency.responsePayload(), InventorySupplierReturnResponse.class).asReplay();
        }

        DbInventoryAdjustmentRepository.SupplierReturnProductSnapshot product = commandRepository
                .findSupplierReturnProductForUpdate(request.companyId(), request.productId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found for this tenant"));
        if (product.trackingType().isSerialized()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SERIALIZED_SUPPLIER_RETURN_REQUIRES_UNIT_COMMAND",
                    "Serialized supplier returns must select exact unit identifiers");
        }
        if (product.supplierId() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PRODUCT_SUPPLIER_REQUIRED", "Product has no supplier for this return");
        }
        if (product.buyingPrice() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SUPPLIER_RETURN_COST_UNAVAILABLE",
                    "A positive server-side inventory cost is required for supplier return posting");
        }

        DbInventoryAdjustmentRepository.BalanceSnapshot before = commandRepository
                .findBalanceForUpdate(request.companyId(), request.branchId(), request.productId())
                .orElseThrow(this::insufficientStock);
        if (before.version() != request.expectedVersion()) {
            throw versionConflict();
        }
        if ((long) before.quantity() - request.quantity() < before.reservedQuantity()) {
            throw insufficientStock();
        }

        int returnAmount = moneyAmount(request.quantity(), product.buyingPrice());
        int refundAmount = request.refundAmount() == null ? 0 : request.refundAmount();
        if (refundAmount > returnAmount) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SUPPLIER_RETURN_REFUND_EXCEEDS_VALUE",
                    "refundAmount cannot exceed the server-calculated supplier return value");
        }

        DbInventoryAdjustmentRepository.BalanceSnapshot after = commandRepository.updateBalance(
                        request.companyId(), request.branchId(), request.productId(), -request.quantity(), request.expectedVersion())
                .orElseThrow(this::versionConflict);

        String note = normalizedNote(request.note());
        long supplierReturnId = supplierReturnRepository.insertCompatibilityReturn(
                request.companyId(), request.branchId(), request.productId(), product.supplierId(),
                request.quantity(), product.buyingPrice(), refundAmount, normalizedActor, note);
        if (supplierReturnId <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SUPPLIER_RETURN_DOCUMENT_CONFLICT",
                    "Supplier return document could not be inserted exactly once");
        }
        supplierReturnRepository.applySupplierReturn(
                        request.companyId(), request.branchId(), product.supplierId(),
                        returnAmount, returnAmount - refundAmount)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SUPPLIER_ACCOUNT_NOT_FOUND",
                        "Supplier account was not found in the selected branch"));

        String operationId = idempotency.operationId();
        InventoryStockMovement movement = new InventoryStockMovement(
                0,
                request.companyId(),
                (long) request.branchId(),
                request.productId(),
                null,
                InventoryMovementType.RETURN,
                BigDecimal.valueOf(-request.quantity()),
                null,
                null,
                "SUPPLIER_RETURN",
                operationId,
                supplierReturnId,
                (long) product.supplierId(),
                null,
                null,
                normalizedActor,
                note,
                "supplier-return:" + operationId,
                null
        );
        long ledgerId = movementRepository.insertHistoryLedgerMovement(
                movement, -returnAmount, refundAmount > 0 ? "CASH" : "CREDIT", -(returnAmount - refundAmount));
        long movementId = movementRepository.insertMovement(movement);
        if (ledgerId <= 0 || movementId <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SUPPLIER_RETURN_MOVEMENT_CONFLICT",
                    "Supplier return movement could not be inserted exactly once");
        }

        SupplierBProduct financeDocument = new SupplierBProduct(
                Math.toIntExact(supplierReturnId),
                Math.toIntExact(product.productId()),
                product.supplierId(),
                request.quantity(),
                product.buyingPrice(),
                normalizedActor,
                refundAmount,
                Timestamp.valueOf(LocalDateTime.now()),
                note,
                0,
                null,
                null,
                null,
                null
        );
        FinancePostingRequestItem financeRequest = financePostingService.enqueueSupplierReturn(
                request.companyId(), request.branchId(), financeDocument);

        InventorySupplierReturnResponse response = new InventorySupplierReturnResponse(
                operationId,
                supplierReturnId,
                product.productId(),
                product.productName(),
                product.supplierId(),
                request.quantity(),
                before.quantity(),
                after.quantity(),
                after.reservedQuantity(),
                after.version(),
                ledgerId,
                movementId,
                new InventorySupplierReturnResponse.FinanceSummary(
                        financeRequest == null ? "SKIPPED" : financeRequest.getStatus(),
                        financeRequest == null || financeRequest.getPostingRequestId() == null
                                ? null : financeRequest.getPostingRequestId().toString()),
                false
        );
        saveReplay(request.companyId(), idempotency.id(), response);
        return response;
    }

    private void validateEnvelope(InventorySupplierReturnRequest request) {
        TenantSqlIdentifiers.requirePositive(request.companyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.branchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.productId(), "productId");
        TenantSqlIdentifiers.requirePositive(request.quantity(), "quantity");
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "EXPECTED_VERSION_INVALID", "expectedVersion must be zero or greater");
        }
        if (request.refundAmount() != null && request.refundAmount() < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "SUPPLIER_RETURN_REFUND_INVALID", "refundAmount must be zero or greater");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
        }
    }

    private int moneyAmount(int quantity, int unitCost) {
        try {
            return Math.multiplyExact(quantity, unitCost);
        } catch (ArithmeticException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SUPPLIER_RETURN_VALUE_OUT_OF_RANGE",
                    "Supplier return value exceeds the legacy money column range");
        }
    }

    private void saveReplay(int companyId, long idempotencyId, InventorySupplierReturnResponse response) {
        try {
            commandRepository.markIdempotencyCompleted(
                    companyId, idempotencyId, objectMapper.writeValueAsString(response));
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "IDEMPOTENCY_RESPONSE_SAVE_FAILED",
                    "Supplier return response could not be persisted for idempotency replay");
        }
    }

    private String hashRequest(InventorySupplierReturnRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(
                    objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hashed) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "REQUEST_HASH_FAILED", "Supplier return request could not be hashed");
        }
    }

    private String normalizeActor(String actorName) {
        if (actorName == null || actorName.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACTOR_REQUIRED", "Authenticated actor is required");
        }
        String normalized = actorName.trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60);
    }

    private String normalizedNote(String note) {
        return note == null || note.isBlank() ? "Inventory product return to supplier" : note.trim();
    }

    private ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVENTORY_BALANCE_VERSION_CONFLICT",
                "Inventory balance changed; reload and retry with the current version");
    }

    private ApiException insufficientStock() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVENTORY_AVAILABLE_QUANTITY_INSUFFICIENT",
                "Supplier return cannot reduce quantity below reserved stock");
    }
}
