package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryAdjustmentRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryDamageRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryDamageRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryDamageReversalRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventoryDamageResponse;
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
public class InventoryDamageService {

    private static final String CREATE_OPERATION = "INVENTORY_DAMAGE";
    private static final String REVERSAL_OPERATION = "INVENTORY_DAMAGE_REVERSAL";

    private final DbInventoryAdjustmentRepository commandRepository;
    private final DbInventoryDamageRepository damageRepository;
    private final DbInventoryStockMovementRepository movementRepository;
    private final FinanceOperationalPostingService financePostingService;
    private final ObjectMapper objectMapper;

    public InventoryDamageService(DbInventoryAdjustmentRepository commandRepository,
                                  DbInventoryDamageRepository damageRepository,
                                  DbInventoryStockMovementRepository movementRepository,
                                  FinanceOperationalPostingService financePostingService,
                                  ObjectMapper objectMapper) {
        this.commandRepository = commandRepository;
        this.damageRepository = damageRepository;
        this.movementRepository = movementRepository;
        this.financePostingService = financePostingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryDamageResponse create(String actorName, InventoryDamageRequest request) {
        validateCreate(request);
        String actor = normalizeActor(actorName);
        DbInventoryAdjustmentRepository.IdempotencyRecord idempotency = lockIdempotency(
                request.companyId(), request.branchId(), CREATE_OPERATION, request.idempotencyKey(),
                hashRequest(request), actor);
        InventoryDamageResponse replay = replayIfCompleted(idempotency);
        if (replay != null) return replay;

        DbInventoryAdjustmentRepository.ProductSnapshot product = commandRepository
                .findProductForUpdate(request.companyId(), request.productId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found for this tenant"));
        if (product.trackingType().isSerialized()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SERIALIZED_DAMAGE_REQUIRES_UNIT_COMMAND",
                    "Serialized damage must select exact IMEI/serial units");
        }
        if (product.buyingPrice() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAMAGE_COST_UNAVAILABLE",
                    "A positive server-side inventory cost is required for damage posting");
        }

        DbInventoryAdjustmentRepository.BalanceSnapshot before = commandRepository
                .findBalanceForUpdate(request.companyId(), request.branchId(), request.productId())
                .orElseThrow(this::insufficientStock);
        long expectedVersion = resolveExpectedVersion(request.expectedVersion(), before.version());
        if ((long) before.quantity() - request.quantity() < before.reservedQuantity()) {
            throw insufficientStock();
        }
        DbInventoryAdjustmentRepository.BalanceSnapshot after = commandRepository.updateBalance(
                        request.companyId(), request.branchId(), request.productId(), -request.quantity(), expectedVersion)
                .orElseThrow(this::versionConflict);
        int inventoryValue = moneyAmount(request.quantity(), product.buyingPrice());
        String reason = request.reason().trim();
        String damagedBy = request.damagedBy().trim();

        long damageId = damageRepository.insertDamage(
                request.companyId(), request.branchId(), request.productId(), product.productName(), request.quantity(),
                product.buyingPrice(), inventoryValue, reason, damagedBy, actor,
                request.liabilityAmount() == null ? 0 : request.liabilityAmount(),
                idempotency.operationId(), after.version());
        if (damageId <= 0) throw writeConflict("DAMAGE_DOCUMENT_CONFLICT", "Damage document could not be inserted exactly once");

        InventoryStockMovement movement = movement(
                request.companyId(), request.branchId(), request.productId(), -request.quantity(),
                InventoryMovementType.DAMAGE, "DAMAGE_DOCUMENT", idempotency.operationId(), damageId,
                actor, reason, "damage:" + idempotency.operationId());
        long ledgerId = movementRepository.insertHistoryLedgerMovement(movement, -inventoryValue, "DAMAGE", 0);
        long movementId = movementRepository.insertMovement(movement);
        assertMovements(ledgerId, movementId);

        FinancePostingRequestItem finance = financePostingService.enqueueInventoryDamageCommand(
                request.companyId(), request.branchId(), actor, damageId, request.productId(), request.quantity(),
                BigDecimal.valueOf(inventoryValue), movementId, reason, LocalDate.now());
        InventoryDamageResponse response = response(
                idempotency.operationId(), damageId, request.productId(), product.productName(), -request.quantity(),
                before, after, "POSTED", ledgerId, movementId, finance);
        saveReplay(request.companyId(), idempotency.id(), response);
        return response;
    }

    @Transactional
    public InventoryDamageResponse reverse(String actorName, InventoryDamageReversalRequest request) {
        validateReversal(request);
        String actor = normalizeActor(actorName);
        DbInventoryAdjustmentRepository.IdempotencyRecord idempotency = lockIdempotency(
                request.companyId(), request.branchId(), REVERSAL_OPERATION, request.idempotencyKey(),
                hashRequest(request), actor);
        InventoryDamageResponse replay = replayIfCompleted(idempotency);
        if (replay != null) return replay;

        DbInventoryDamageRepository.DamageSnapshot damage = damageRepository
                .findForUpdate(request.companyId(), request.branchId(), request.damageId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "DAMAGE_DOCUMENT_NOT_FOUND", "Damage document was not found"));
        if (!"POSTED".equalsIgnoreCase(damage.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "DAMAGE_ALREADY_REVERSED", "Damage document is already reversed");
        }
        if (damage.paid()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLED_DAMAGE_REVERSAL_REQUIRES_FINANCE_WORKFLOW",
                    "A settled damage liability must be reversed through the finance workflow first");
        }
        if (damage.unitCost() <= 0 || damage.inventoryValue() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAMAGE_ORIGINAL_VALUATION_UNAVAILABLE",
                    "Legacy damage without original valuation cannot be automatically reversed");
        }

        DbInventoryAdjustmentRepository.BalanceSnapshot before = commandRepository
                .findBalanceForUpdate(request.companyId(), request.branchId(), damage.productId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "INVENTORY_BALANCE_NOT_FOUND", "Inventory balance was not found"));
        long expectedVersion = resolveExpectedVersion(request.expectedVersion(), before.version());
        DbInventoryAdjustmentRepository.BalanceSnapshot after = commandRepository.updateBalance(
                        request.companyId(), request.branchId(), damage.productId(), damage.quantity(), expectedVersion)
                .orElseThrow(this::versionConflict);
        if (!damageRepository.markReversed(
                request.companyId(), request.branchId(), request.damageId(), actor, request.reason().trim(),
                idempotency.operationId(), after.version())) {
            throw writeConflict("DAMAGE_REVERSAL_CONFLICT", "Damage document could not be reversed exactly once");
        }

        InventoryStockMovement movement = movement(
                request.companyId(), request.branchId(), damage.productId(), damage.quantity(),
                InventoryMovementType.ADJUSTMENT, "DAMAGE_REVERSAL", idempotency.operationId(), damage.damageId(),
                actor, request.reason().trim(), "damage-reversal:" + idempotency.operationId());
        long ledgerId = movementRepository.insertHistoryLedgerMovement(
                movement, damage.inventoryValue(), "DAMAGE_REVERSAL", 0);
        long movementId = movementRepository.insertMovement(movement);
        assertMovements(ledgerId, movementId);

        FinancePostingRequestItem finance = financePostingService.enqueueInventoryAdjustmentCommand(
                request.companyId(), request.branchId(), actor, idempotency.operationId(), damage.productId(),
                damage.quantity(), BigDecimal.valueOf(damage.inventoryValue()), movementId,
                "DAMAGE_REVERSAL", request.reason().trim(), LocalDate.now());
        InventoryDamageResponse response = response(
                idempotency.operationId(), damage.damageId(), damage.productId(), damage.productName(), damage.quantity(),
                before, after, "REVERSED", ledgerId, movementId, finance);
        saveReplay(request.companyId(), idempotency.id(), response);
        return response;
    }

    private DbInventoryAdjustmentRepository.IdempotencyRecord lockIdempotency(
            int companyId, int branchId, String operationType, String idempotencyKey,
            String requestHash, String actor) {
        String key = idempotencyKey.trim();
        commandRepository.insertPendingIdempotency(
                companyId, branchId, operationType, key, requestHash, actor, UUID.randomUUID().toString());
        DbInventoryAdjustmentRepository.IdempotencyRecord record = commandRepository
                .findIdempotencyForUpdate(companyId, branchId, operationType, key)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_STATE_MISSING", "Damage idempotency state could not be locked"));
        if (!requestHash.equals(record.requestHash())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                    "The same idempotency key was already used with a different damage payload");
        }
        return record;
    }

    private InventoryDamageResponse replayIfCompleted(DbInventoryAdjustmentRepository.IdempotencyRecord record) {
        if ("COMPLETED".equalsIgnoreCase(record.status()) && record.responsePayload() != null) {
            return objectMapper.convertValue(record.responsePayload(), InventoryDamageResponse.class).asReplay();
        }
        return null;
    }

    private InventoryStockMovement movement(int companyId, int branchId, long productId, int delta,
                                             InventoryMovementType type, String referenceType, String operationId,
                                             long damageId, String actor, String note, String movementKey) {
        return new InventoryStockMovement(
                0, companyId, (long) branchId, productId, null, type, BigDecimal.valueOf(delta),
                null, null, referenceType, operationId, damageId, null, null, null,
                actor, note, movementKey, null);
    }

    private InventoryDamageResponse response(String operationId, long damageId, long productId, String productName,
                                               int delta, DbInventoryAdjustmentRepository.BalanceSnapshot before,
                                               DbInventoryAdjustmentRepository.BalanceSnapshot after, String status,
                                               long ledgerId, long movementId, FinancePostingRequestItem finance) {
        return new InventoryDamageResponse(
                operationId, damageId, productId, productName, delta, before.quantity(), after.quantity(),
                after.reservedQuantity(), after.version(), status, ledgerId, movementId,
                new InventoryDamageResponse.FinanceSummary(
                        finance == null ? "SKIPPED" : finance.getStatus(),
                        finance == null || finance.getPostingRequestId() == null
                                ? null : finance.getPostingRequestId().toString()), false);
    }

    private long resolveExpectedVersion(Long requestedVersion, long currentVersion) {
        if (requestedVersion != null && requestedVersion != currentVersion) throw versionConflict();
        return currentVersion;
    }

    private void validateCreate(InventoryDamageRequest request) {
        TenantSqlIdentifiers.requirePositive(request.companyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.branchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.productId(), "productId");
        TenantSqlIdentifiers.requirePositive(request.quantity(), "quantity");
        if (request.expectedVersion() != null && request.expectedVersion() < 0) throw versionConflict();
        if (request.reason() == null || request.reason().isBlank()) throw required("DAMAGE_REASON_REQUIRED", "reason is required");
        if (request.damagedBy() == null || request.damagedBy().isBlank()) throw required("DAMAGED_BY_REQUIRED", "damagedBy is required");
        if (request.liabilityAmount() != null && request.liabilityAmount() < 0) throw required("DAMAGE_LIABILITY_INVALID", "liabilityAmount must be zero or greater");
        requireKey(request.idempotencyKey());
    }

    private void validateReversal(InventoryDamageReversalRequest request) {
        TenantSqlIdentifiers.requirePositive(request.companyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.branchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.damageId(), "damageId");
        if (request.expectedVersion() != null && request.expectedVersion() < 0) throw versionConflict();
        if (request.reason() == null || request.reason().isBlank()) throw required("DAMAGE_REVERSAL_REASON_REQUIRED", "reason is required");
        requireKey(request.idempotencyKey());
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) throw required("IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
    }

    private int moneyAmount(int quantity, int unitCost) {
        try {
            return Math.multiplyExact(quantity, unitCost);
        } catch (ArithmeticException exception) {
            throw required("DAMAGE_VALUE_OUT_OF_RANGE", "Damage value exceeds the legacy money column range");
        }
    }

    private String hashRequest(Object request) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(
                    objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hashed) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception exception) {
            throw required("REQUEST_HASH_FAILED", "Damage request could not be hashed");
        }
    }

    private void saveReplay(int companyId, long id, InventoryDamageResponse response) {
        try {
            commandRepository.markIdempotencyCompleted(companyId, id, objectMapper.writeValueAsString(response));
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "IDEMPOTENCY_RESPONSE_SAVE_FAILED",
                    "Damage response could not be persisted for idempotency replay");
        }
    }

    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "ACTOR_REQUIRED", "Authenticated actor is required");
        String normalized = actor.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private void assertMovements(long ledgerId, long movementId) {
        if (ledgerId <= 0 || movementId <= 0) throw writeConflict(
                "DAMAGE_MOVEMENT_CONFLICT", "Damage movement could not be inserted exactly once");
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "INVENTORY_BALANCE_VERSION_CONFLICT", "Inventory balance changed; reload and retry");
    }

    private ApiException insufficientStock() {
        return new ApiException(HttpStatus.CONFLICT, "INVENTORY_AVAILABLE_QUANTITY_INSUFFICIENT", "Damage cannot consume reserved stock");
    }

    private ApiException required(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException writeConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
