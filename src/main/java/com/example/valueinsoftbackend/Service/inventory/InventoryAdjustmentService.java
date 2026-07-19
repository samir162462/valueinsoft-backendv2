package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryAdjustmentRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Inventory.InventoryAdjustmentReason;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryAdjustmentRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventoryAdjustmentResponse;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class InventoryAdjustmentService {

    private static final String OPERATION_TYPE = "INVENTORY_ADJUSTMENT";

    private final DbInventoryAdjustmentRepository adjustmentRepository;
    private final DbInventoryStockMovementRepository movementRepository;
    private final FinanceOperationalPostingService financePostingService;
    private final ObjectMapper objectMapper;

    public InventoryAdjustmentService(DbInventoryAdjustmentRepository adjustmentRepository,
                                      DbInventoryStockMovementRepository movementRepository,
                                      FinanceOperationalPostingService financePostingService,
                                      ObjectMapper objectMapper) {
        this.adjustmentRepository = adjustmentRepository;
        this.movementRepository = movementRepository;
        this.financePostingService = financePostingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryAdjustmentResponse adjust(String actorName, InventoryAdjustmentRequest request) {
        validateEnvelope(request);
        String requestHash = hashRequest(request);
        String proposedOperationId = UUID.randomUUID().toString();
        adjustmentRepository.insertPendingIdempotency(
                request.companyId(),
                request.branchId(),
                OPERATION_TYPE,
                request.idempotencyKey().trim(),
                requestHash,
                actorName,
                proposedOperationId
        );

        DbInventoryAdjustmentRepository.IdempotencyRecord idempotency = adjustmentRepository.findIdempotencyForUpdate(
                        request.companyId(),
                        request.branchId(),
                        OPERATION_TYPE,
                        request.idempotencyKey().trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "IDEMPOTENCY_STATE_MISSING",
                        "Adjustment idempotency state could not be locked"
                ));
        if (!requestHash.equals(idempotency.requestHash())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                    "The same idempotency key was already used with a different adjustment payload"
            );
        }
        if ("COMPLETED".equalsIgnoreCase(idempotency.status()) && idempotency.responsePayload() != null) {
            return objectMapper.convertValue(idempotency.responsePayload(), InventoryAdjustmentResponse.class).asReplay();
        }

        DbInventoryAdjustmentRepository.ProductSnapshot product = adjustmentRepository
                .findProductForUpdate(request.companyId(), request.productId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found for this tenant"));
        if (product.trackingType().isSerialized()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SERIALIZED_ADJUSTMENT_REQUIRES_UNIT_COMMAND",
                    "Serialized inventory must be adjusted through a unit-specific workflow"
            );
        }
        if (product.buyingPrice() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVENTORY_ADJUSTMENT_COST_UNAVAILABLE",
                    "A positive server-side inventory cost is required for adjustment posting"
            );
        }

        DbInventoryAdjustmentRepository.BalanceSnapshot before = adjustmentRepository
                .findBalanceForUpdate(request.companyId(), request.branchId(), request.productId())
                .orElse(null);
        DbInventoryAdjustmentRepository.BalanceSnapshot after = applyBalance(request, before);
        int previousQuantity = before == null ? 0 : before.quantity();

        String operationId = idempotency.operationId();
        String movementIdempotencyKey = "adjustment:" + operationId;
        InventoryStockMovement movement = new InventoryStockMovement(
                0,
                request.companyId(),
                (long) request.branchId(),
                request.productId(),
                null,
                InventoryMovementType.ADJUSTMENT,
                BigDecimal.valueOf(request.quantityDelta()),
                null,
                null,
                "INVENTORY_ADJUSTMENT",
                operationId,
                null,
                null,
                null,
                null,
                actorName,
                adjustmentNote(request),
                movementIdempotencyKey,
                null
        );

        int signedTotal = signedLegacyTotal(request.quantityDelta(), product.buyingPrice());
        long ledgerId = movementRepository.insertHistoryLedgerMovement(movement, signedTotal, "SYSTEM", 0);
        long movementId = movementRepository.insertMovement(movement);
        if (ledgerId <= 0 || movementId <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVENTORY_ADJUSTMENT_MOVEMENT_CONFLICT",
                    "Adjustment movement could not be inserted exactly once"
            );
        }

        FinancePostingRequestItem financeRequest = financePostingService.enqueueInventoryAdjustmentCommand(
                request.companyId(),
                request.branchId(),
                actorName,
                operationId,
                request.productId(),
                request.quantityDelta(),
                BigDecimal.valueOf(signedTotal).abs(),
                movementId,
                request.reasonCode().name(),
                request.note(),
                LocalDate.now()
        );
        InventoryAdjustmentResponse response = new InventoryAdjustmentResponse(
                operationId,
                product.productId(),
                product.productName(),
                request.quantityDelta(),
                previousQuantity,
                after.quantity(),
                after.reservedQuantity(),
                after.version(),
                ledgerId,
                movementId,
                new InventoryAdjustmentResponse.FinanceSummary(
                        financeRequest == null ? "SKIPPED" : financeRequest.getStatus(),
                        financeRequest == null || financeRequest.getPostingRequestId() == null
                                ? null
                                : financeRequest.getPostingRequestId().toString()
                ),
                false
        );
        try {
            adjustmentRepository.markIdempotencyCompleted(
                    request.companyId(),
                    idempotency.id(),
                    objectMapper.writeValueAsString(response)
            );
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "IDEMPOTENCY_RESPONSE_SAVE_FAILED",
                    "Adjustment response could not be persisted for idempotency replay"
            );
        }
        return response;
    }

    private DbInventoryAdjustmentRepository.BalanceSnapshot applyBalance(
            InventoryAdjustmentRequest request,
            DbInventoryAdjustmentRepository.BalanceSnapshot before) {
        if (before == null) {
            if (request.expectedVersion() != 0) {
                throw versionConflict();
            }
            if (request.quantityDelta() < 0) {
                throw insufficientStock();
            }
            return adjustmentRepository.insertBalance(
                            request.companyId(), request.branchId(), request.productId(), request.quantityDelta())
                    .orElseThrow(this::versionConflict);
        }

        if (before.version() != request.expectedVersion()) {
            throw versionConflict();
        }
        long nextQuantity = (long) before.quantity() + request.quantityDelta();
        if (nextQuantity < before.reservedQuantity() || nextQuantity > Integer.MAX_VALUE) {
            throw insufficientStock();
        }
        return adjustmentRepository.updateBalance(
                        request.companyId(),
                        request.branchId(),
                        request.productId(),
                        request.quantityDelta(),
                        request.expectedVersion())
                .orElseThrow(this::versionConflict);
    }

    private void validateEnvelope(InventoryAdjustmentRequest request) {
        TenantSqlIdentifiers.requirePositive(request.companyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.branchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.productId(), "productId");
        if (request.quantityDelta() == null || request.quantityDelta() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADJUSTMENT_DELTA_REQUIRED", "quantityDelta must be non-zero");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPECTED_VERSION_INVALID", "expectedVersion must be zero or greater");
        }
        if (request.reasonCode() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADJUSTMENT_REASON_REQUIRED", "reasonCode is required");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
        }
        if (request.reasonCode() == InventoryAdjustmentReason.FOUND_STOCK && request.quantityDelta() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADJUSTMENT_REASON_DIRECTION_INVALID", "FOUND_STOCK requires a positive quantityDelta");
        }
        if (request.reasonCode() == InventoryAdjustmentReason.SHRINKAGE && request.quantityDelta() > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADJUSTMENT_REASON_DIRECTION_INVALID", "SHRINKAGE requires a negative quantityDelta");
        }
    }

    private int signedLegacyTotal(int quantityDelta, int buyingPrice) {
        try {
            return BigDecimal.valueOf(quantityDelta)
                    .multiply(BigDecimal.valueOf(buyingPrice))
                    .intValueExact();
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADJUSTMENT_VALUE_OUT_OF_RANGE", "Adjustment value exceeds the legacy money column range");
        }
    }

    private String adjustmentNote(InventoryAdjustmentRequest request) {
        String reason = request.reasonCode().name();
        return request.note() == null || request.note().isBlank() ? reason : reason + ": " + request.note().trim();
    }

    private String hashRequest(InventoryAdjustmentRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hashed) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_HASH_FAILED", "Adjustment request could not be hashed");
        }
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "INVENTORY_BALANCE_VERSION_CONFLICT", "Inventory balance changed; reload and retry with the current version");
    }

    private ApiException insufficientStock() {
        return new ApiException(HttpStatus.CONFLICT, "INVENTORY_AVAILABLE_QUANTITY_INSUFFICIENT", "Adjustment cannot reduce quantity below reserved stock");
    }
}
