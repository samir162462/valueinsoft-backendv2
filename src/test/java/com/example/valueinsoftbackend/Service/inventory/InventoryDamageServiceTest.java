package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryAdjustmentRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryDamageRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryDamageRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryDamageReversalRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventoryDamageResponse;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

class InventoryDamageServiceTest {

    private DbInventoryAdjustmentRepository commandRepository;
    private DbInventoryDamageRepository damageRepository;
    private DbInventoryStockMovementRepository movementRepository;
    private FinanceOperationalPostingService financeService;
    private ObjectMapper objectMapper;
    private InventoryDamageService service;

    @BeforeEach
    void setUp() {
        commandRepository = mock(DbInventoryAdjustmentRepository.class);
        damageRepository = mock(DbInventoryDamageRepository.class);
        movementRepository = mock(DbInventoryStockMovementRepository.class);
        financeService = mock(FinanceOperationalPostingService.class);
        objectMapper = new ObjectMapper();
        service = new InventoryDamageService(
                commandRepository, damageRepository, movementRepository, financeService, objectMapper);
    }

    @Test
    void createsDamageUsingServerCostAndPostsEverythingBeforeCompletion() {
        stubIdempotency("INVENTORY_DAMAGE", "damage-key", "damage-operation");
        when(commandRepository.findProductForUpdate(10, 30)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.ProductSnapshot(30, "Phone", 50, TrackingType.QUANTITY)));
        when(commandRepository.findBalanceForUpdate(10, 20, 30)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(10, 2, 4)));
        when(commandRepository.updateBalance(10, 20, 30, -3, 4)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(7, 2, 5)));
        when(damageRepository.insertDamage(
                10, 20, 30, "Phone", 3, 50, 150, "Broken", "Operator", "manager", 25,
                "damage-operation", 5)).thenReturn(201L);
        when(movementRepository.insertHistoryLedgerMovement(any(), eq(-150), eq("DAMAGE"), eq(0))).thenReturn(301L);
        when(movementRepository.insertMovement(any())).thenReturn(302L);
        FinancePostingRequestItem finance = mock(FinancePostingRequestItem.class);
        when(finance.getStatus()).thenReturn("PENDING");
        when(financeService.enqueueInventoryDamageCommand(
                eq(10), eq(20), eq("manager"), eq(201L), eq(30L), eq(3),
                eq(new BigDecimal("150")), eq(302L), eq("Broken"), any())).thenReturn(finance);

        InventoryDamageResponse response = service.create("manager", createRequest());

        assertEquals(-3, response.quantityDelta());
        assertEquals(7, response.newQuantity());
        assertEquals(5, response.balanceVersion());
        assertEquals("POSTED", response.status());
        verify(financeService).enqueueInventoryDamageCommand(
                eq(10), eq(20), eq("manager"), eq(201L), eq(30L), eq(3),
                eq(new BigDecimal("150")), eq(302L), eq("Broken"), any());
    }

    @Test
    void completedDamageReplaysWithoutWrites() {
        InventoryDamageResponse saved = new InventoryDamageResponse(
                "damage-operation", 201, 30, "Phone", -3, 10, 7, 2, 5, "POSTED", 301, 302,
                new InventoryDamageResponse.FinanceSummary("PENDING", null), false);
        AtomicReference<String> hash = new AtomicReference<>();
        doAnswer(invocation -> { hash.set(invocation.getArgument(4)); return null; })
                .when(commandRepository).insertPendingIdempotency(
                        anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(commandRepository.findIdempotencyForUpdate(10, 20, "INVENTORY_DAMAGE", "damage-key"))
                .thenAnswer(invocation -> Optional.of(new DbInventoryAdjustmentRepository.IdempotencyRecord(
                        1, hash.get(), "COMPLETED", objectMapper.valueToTree(saved), "damage-operation")));

        InventoryDamageResponse response = service.create("manager", createRequest());

        assertTrue(response.idempotentReplay());
        verify(commandRepository, never()).findProductForUpdate(anyInt(), anyLong());
    }

    @Test
    void aggregateSerializedDamageIsRejected() {
        stubIdempotency("INVENTORY_DAMAGE", "damage-key", "damage-operation");
        when(commandRepository.findProductForUpdate(10, 30)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.ProductSnapshot(30, "Phone", 50, TrackingType.IMEI)));

        ApiException exception = assertThrows(ApiException.class, () -> service.create("manager", createRequest()));

        assertEquals("SERIALIZED_DAMAGE_REQUIRES_UNIT_COMMAND", exception.getCode());
    }

    @Test
    void reversesUnsettledDamageUsingOriginalValuation() {
        stubIdempotency("INVENTORY_DAMAGE_REVERSAL", "reversal-key", "reversal-operation");
        when(damageRepository.findForUpdate(10, 20, 201)).thenReturn(Optional.of(
                new DbInventoryDamageRepository.DamageSnapshot(
                        201, 30, "Phone", 3, "Broken", "Operator", "manager", 25,
                        false, "POSTED", 50, 150)));
        when(commandRepository.findBalanceForUpdate(10, 20, 30)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(7, 2, 5)));
        when(commandRepository.updateBalance(10, 20, 30, 3, 5)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(10, 2, 6)));
        when(damageRepository.markReversed(
                10, 20, 201, "manager", "Entered by mistake", "reversal-operation", 6)).thenReturn(true);
        when(movementRepository.insertHistoryLedgerMovement(any(InventoryStockMovement.class), eq(150), eq("DAMAGE_REVERSAL"), eq(0))).thenReturn(401L);
        when(movementRepository.insertMovement(any())).thenReturn(402L);

        InventoryDamageResponse response = service.reverse("manager", reversalRequest());

        assertEquals(3, response.quantityDelta());
        assertEquals(10, response.newQuantity());
        assertEquals("REVERSED", response.status());
        verify(financeService).enqueueInventoryAdjustmentCommand(
                eq(10), eq(20), eq("manager"), eq("reversal-operation"), eq(30L), eq(3),
                eq(new BigDecimal("150")), eq(402L), eq("DAMAGE_REVERSAL"), eq("Entered by mistake"), any());
    }

    @Test
    void settledDamageCannotBeReversedByInventoryWorkflow() {
        stubIdempotency("INVENTORY_DAMAGE_REVERSAL", "reversal-key", "reversal-operation");
        when(damageRepository.findForUpdate(10, 20, 201)).thenReturn(Optional.of(
                new DbInventoryDamageRepository.DamageSnapshot(
                        201, 30, "Phone", 3, "Broken", "Operator", "manager", 25,
                        true, "POSTED", 50, 150)));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.reverse("manager", reversalRequest()));

        assertEquals("SETTLED_DAMAGE_REVERSAL_REQUIRES_FINANCE_WORKFLOW", exception.getCode());
        verify(commandRepository, never()).updateBalance(anyInt(), anyInt(), anyLong(), anyInt(), anyLong());
    }

    private void stubIdempotency(String operationType, String key, String operationId) {
        AtomicReference<String> hash = new AtomicReference<>();
        doAnswer(invocation -> { hash.set(invocation.getArgument(4)); return null; })
                .when(commandRepository).insertPendingIdempotency(
                        anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(commandRepository.findIdempotencyForUpdate(10, 20, operationType, key))
                .thenAnswer(invocation -> Optional.of(new DbInventoryAdjustmentRepository.IdempotencyRecord(
                        1, hash.get(), "PENDING", null, operationId)));
    }

    private InventoryDamageRequest createRequest() {
        return new InventoryDamageRequest(10, 20, 30, 3, 4L, "Broken", "Operator", 25, "damage-key");
    }

    private InventoryDamageReversalRequest reversalRequest() {
        return new InventoryDamageReversalRequest(
                10, 20, 201, 5L, "Entered by mistake", "reversal-key");
    }
}
