package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryAdjustmentRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Inventory.InventoryAdjustmentReason;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryAdjustmentRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventoryAdjustmentResponse;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryAdjustmentServiceTest {

    private DbInventoryAdjustmentRepository adjustmentRepository;
    private DbInventoryStockMovementRepository movementRepository;
    private FinanceOperationalPostingService financePostingService;
    private ObjectMapper objectMapper;
    private InventoryAdjustmentService service;

    @BeforeEach
    void setUp() {
        adjustmentRepository = mock(DbInventoryAdjustmentRepository.class);
        movementRepository = mock(DbInventoryStockMovementRepository.class);
        financePostingService = mock(FinanceOperationalPostingService.class);
        objectMapper = new ObjectMapper();
        service = new InventoryAdjustmentService(
                adjustmentRepository,
                movementRepository,
                financePostingService,
                objectMapper
        );
    }

    @Test
    void createsVersionedQuantityAdjustmentAndFinanceRequestAtomically() {
        stubPendingIdempotency("operation-1");
        when(adjustmentRepository.findProductForUpdate(10, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.ProductSnapshot(30, "Phone", 50, TrackingType.QUANTITY)
        ));
        when(adjustmentRepository.findBalanceForUpdate(10, 20, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(10, 2, 4)
        ));
        when(adjustmentRepository.updateBalance(10, 20, 30L, -3, 4)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(7, 2, 5)
        ));
        when(movementRepository.insertHistoryLedgerMovement(any(), eq(-150), eq("SYSTEM"), eq(0))).thenReturn(101L);
        when(movementRepository.insertMovement(any())).thenReturn(102L);
        FinancePostingRequestItem finance = mock(FinancePostingRequestItem.class);
        UUID postingId = UUID.randomUUID();
        when(finance.getStatus()).thenReturn("PENDING");
        when(finance.getPostingRequestId()).thenReturn(postingId);
        when(financePostingService.enqueueInventoryAdjustmentCommand(
                eq(10), eq(20), eq("manager"), eq("operation-1"), eq(30L), eq(-3),
                eq(new BigDecimal("150")), eq(102L), eq("SHRINKAGE"), eq("cycle count"), any()
        )).thenReturn(finance);

        InventoryAdjustmentResponse response = service.adjust("manager", request(-3, 4, InventoryAdjustmentReason.SHRINKAGE, "key-1"));

        assertEquals(10, response.previousQuantity());
        assertEquals(7, response.newQuantity());
        assertEquals(5, response.balanceVersion());
        assertEquals(101, response.ledgerId());
        assertEquals(102, response.movementId());
        assertEquals(postingId.toString(), response.finance().postingRequestId());
        assertFalse(response.idempotentReplay());

        ArgumentCaptor<InventoryStockMovement> movementCaptor = ArgumentCaptor.forClass(InventoryStockMovement.class);
        verify(movementRepository).insertMovement(movementCaptor.capture());
        assertEquals("manager", movementCaptor.getValue().getActorName());
        assertEquals("SHRINKAGE: cycle count", movementCaptor.getValue().getNote());
        assertEquals(new BigDecimal("-3"), movementCaptor.getValue().getQuantityDelta());
    }

    @Test
    void completedIdempotencyRecordReplaysWithoutWriting() {
        InventoryAdjustmentResponse saved = new InventoryAdjustmentResponse(
                "operation-1", 30, "Phone", 2, 5, 7, 0, 3, 11, 12,
                new InventoryAdjustmentResponse.FinanceSummary("PENDING", null), false
        );
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            return null;
        }).when(adjustmentRepository).insertPendingIdempotency(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(adjustmentRepository.findIdempotencyForUpdate(10, 20, "INVENTORY_ADJUSTMENT", "key-1"))
                .thenAnswer(invocation -> Optional.of(new DbInventoryAdjustmentRepository.IdempotencyRecord(
                        1, requestHash.get(), "COMPLETED", objectMapper.valueToTree(saved), "operation-1"
                )));

        InventoryAdjustmentResponse response = service.adjust("manager", request(2, 2, InventoryAdjustmentReason.FOUND_STOCK, "key-1"));

        assertTrue(response.idempotentReplay());
        verify(adjustmentRepository, never()).findProductForUpdate(anyInt(), anyLong());
        verify(movementRepository, never()).insertMovement(any());
    }

    @Test
    void sameKeyWithDifferentPayloadIsConflict() {
        when(adjustmentRepository.findIdempotencyForUpdate(10, 20, "INVENTORY_ADJUSTMENT", "key-1"))
                .thenReturn(Optional.of(new DbInventoryAdjustmentRepository.IdempotencyRecord(
                        1, "different-hash", "COMPLETED", null, "operation-1"
                )));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.adjust("manager", request(2, 0, InventoryAdjustmentReason.FOUND_STOCK, "key-1")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("IDEMPOTENCY_KEY_PAYLOAD_CONFLICT", exception.getCode());
    }

    @Test
    void serializedProductRequiresUnitSpecificCommand() {
        stubPendingIdempotency("operation-1");
        when(adjustmentRepository.findProductForUpdate(10, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.ProductSnapshot(30, "IMEI Phone", 50, TrackingType.IMEI)
        ));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.adjust("manager", request(1, 0, InventoryAdjustmentReason.FOUND_STOCK, "key-1")));

        assertEquals("SERIALIZED_ADJUSTMENT_REQUIRES_UNIT_COMMAND", exception.getCode());
        verify(adjustmentRepository, never()).findBalanceForUpdate(anyInt(), anyInt(), anyLong());
    }

    @Test
    void staleVersionCannotChangeBalance() {
        stubPendingIdempotency("operation-1");
        when(adjustmentRepository.findProductForUpdate(10, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.ProductSnapshot(30, "Phone", 50, TrackingType.QUANTITY)
        ));
        when(adjustmentRepository.findBalanceForUpdate(10, 20, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(10, 2, 5)
        ));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.adjust("manager", request(-2, 4, InventoryAdjustmentReason.COUNT_VARIANCE, "key-1")));

        assertEquals("INVENTORY_BALANCE_VERSION_CONFLICT", exception.getCode());
        verify(adjustmentRepository, never()).updateBalance(anyInt(), anyInt(), anyLong(), anyInt(), anyLong());
    }

    private void stubPendingIdempotency(String operationId) {
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            return null;
        }).when(adjustmentRepository).insertPendingIdempotency(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(adjustmentRepository.findIdempotencyForUpdate(10, 20, "INVENTORY_ADJUSTMENT", "key-1"))
                .thenAnswer(invocation -> Optional.of(new DbInventoryAdjustmentRepository.IdempotencyRecord(
                        1, requestHash.get(), "PENDING", null, operationId
                )));
    }

    private InventoryAdjustmentRequest request(int delta,
                                               long expectedVersion,
                                               InventoryAdjustmentReason reason,
                                               String idempotencyKey) {
        return new InventoryAdjustmentRequest(
                10,
                20,
                30,
                delta,
                expectedVersion,
                reason,
                "cycle count",
                idempotencyKey
        );
    }
}
