package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryAdjustmentRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventorySupplierReturnRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventorySupplierReturnRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventorySupplierReturnResponse;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
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

class InventorySupplierReturnServiceTest {

    private DbInventoryAdjustmentRepository commandRepository;
    private DbInventorySupplierReturnRepository supplierReturnRepository;
    private DbInventoryStockMovementRepository movementRepository;
    private FinanceOperationalPostingService financePostingService;
    private ObjectMapper objectMapper;
    private InventorySupplierReturnService service;

    @BeforeEach
    void setUp() {
        commandRepository = mock(DbInventoryAdjustmentRepository.class);
        supplierReturnRepository = mock(DbInventorySupplierReturnRepository.class);
        movementRepository = mock(DbInventoryStockMovementRepository.class);
        financePostingService = mock(FinanceOperationalPostingService.class);
        objectMapper = new ObjectMapper();
        service = new InventorySupplierReturnService(
                commandRepository, supplierReturnRepository, movementRepository, financePostingService, objectMapper);
    }

    @Test
    void postsStockSupplierAccountMovementsAndFinanceAtomicallyUsingServerCost() {
        stubPendingIdempotency("return-operation-1");
        when(commandRepository.findSupplierReturnProductForUpdate(10, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.SupplierReturnProductSnapshot(
                        30, "Phone", 50, 9, TrackingType.QUANTITY)));
        when(commandRepository.findBalanceForUpdate(10, 20, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(10, 2, 4)));
        when(commandRepository.updateBalance(10, 20, 30L, -3, 4)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(7, 2, 5)));
        when(supplierReturnRepository.insertCompatibilityReturn(
                10, 20, 30, 9, 3, 50, 25, "manager", "Damaged packaging")).thenReturn(201L);
        when(supplierReturnRepository.applySupplierReturn(10, 20, 9, 150, 125)).thenReturn(Optional.of(
                new DbInventorySupplierReturnRepository.SupplierAccountSnapshot(850, 275)));
        when(movementRepository.insertHistoryLedgerMovement(any(), eq(-150), eq("CASH"), eq(-125)))
                .thenReturn(301L);
        when(movementRepository.insertMovement(any())).thenReturn(302L);
        FinancePostingRequestItem finance = mock(FinancePostingRequestItem.class);
        UUID postingId = UUID.randomUUID();
        when(finance.getStatus()).thenReturn("PENDING");
        when(finance.getPostingRequestId()).thenReturn(postingId);
        when(financePostingService.enqueueSupplierReturn(eq(10), eq(20), any())).thenReturn(finance);

        InventorySupplierReturnResponse response = service.create("manager", request(3, 4, 25, "return-key-1"));

        assertEquals(10, response.previousQuantity());
        assertEquals(7, response.newQuantity());
        assertEquals(5, response.balanceVersion());
        assertEquals(201, response.supplierReturnId());
        assertEquals(postingId.toString(), response.finance().postingRequestId());

        ArgumentCaptor<InventoryStockMovement> movementCaptor = ArgumentCaptor.forClass(InventoryStockMovement.class);
        verify(movementRepository).insertMovement(movementCaptor.capture());
        assertEquals(InventoryMovementType.RETURN, movementCaptor.getValue().getMovementType());
        assertEquals(new BigDecimal("-3"), movementCaptor.getValue().getQuantityDelta());
        assertEquals(9L, movementCaptor.getValue().getSupplierId());

        ArgumentCaptor<SupplierBProduct> financeDocument = ArgumentCaptor.forClass(SupplierBProduct.class);
        verify(financePostingService).enqueueSupplierReturn(eq(10), eq(20), financeDocument.capture());
        assertEquals(50, financeDocument.getValue().getCost());
        assertEquals(9, financeDocument.getValue().getSupplierId());
        assertEquals("manager", financeDocument.getValue().getUserName());
    }

    @Test
    void completedRequestReplaysWithoutStockOrFinanceWrites() {
        InventorySupplierReturnResponse saved = new InventorySupplierReturnResponse(
                "return-operation-1", 201, 30, "Phone", 9, 2, 8, 6, 1, 3, 301, 302,
                new InventorySupplierReturnResponse.FinanceSummary("PENDING", null), false);
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            return null;
        }).when(commandRepository).insertPendingIdempotency(
                anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(commandRepository.findIdempotencyForUpdate(10, 20, "INVENTORY_SUPPLIER_RETURN", "return-key-1"))
                .thenAnswer(invocation -> Optional.of(new DbInventoryAdjustmentRepository.IdempotencyRecord(
                        1, requestHash.get(), "COMPLETED", objectMapper.valueToTree(saved), "return-operation-1")));

        InventorySupplierReturnResponse response = service.create("manager", request(2, 2, 0, "return-key-1"));

        assertTrue(response.idempotentReplay());
        verify(commandRepository, never()).findSupplierReturnProductForUpdate(anyInt(), anyLong());
        verify(movementRepository, never()).insertMovement(any());
        verify(financePostingService, never()).enqueueSupplierReturn(anyInt(), anyInt(), any());
    }

    @Test
    void serializedProductRequiresExactUnitCommand() {
        stubPendingIdempotency("return-operation-1");
        when(commandRepository.findSupplierReturnProductForUpdate(10, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.SupplierReturnProductSnapshot(
                        30, "IMEI Phone", 50, 9, TrackingType.IMEI)));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create("manager", request(1, 0, 0, "return-key-1")));

        assertEquals("SERIALIZED_SUPPLIER_RETURN_REQUIRES_UNIT_COMMAND", exception.getCode());
        verify(commandRepository, never()).findBalanceForUpdate(anyInt(), anyInt(), anyLong());
    }

    @Test
    void staleVersionCannotCreateReturnDocument() {
        stubProductAndBalance(5);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create("manager", request(2, 4, 0, "return-key-1")));

        assertEquals("INVENTORY_BALANCE_VERSION_CONFLICT", exception.getCode());
        verify(supplierReturnRepository, never()).insertCompatibilityReturn(
                anyInt(), anyInt(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void refundCannotExceedServerCalculatedReturnValue() {
        stubProductAndBalance(4);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create("manager", request(2, 4, 101, "return-key-1")));

        assertEquals("SUPPLIER_RETURN_REFUND_EXCEEDS_VALUE", exception.getCode());
        verify(commandRepository, never()).updateBalance(anyInt(), anyInt(), anyLong(), anyInt(), anyLong());
    }

    private void stubProductAndBalance(long version) {
        stubPendingIdempotency("return-operation-1");
        when(commandRepository.findSupplierReturnProductForUpdate(10, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.SupplierReturnProductSnapshot(
                        30, "Phone", 50, 9, TrackingType.QUANTITY)));
        when(commandRepository.findBalanceForUpdate(10, 20, 30L)).thenReturn(Optional.of(
                new DbInventoryAdjustmentRepository.BalanceSnapshot(10, 2, version)));
    }

    private void stubPendingIdempotency(String operationId) {
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            return null;
        }).when(commandRepository).insertPendingIdempotency(
                anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(commandRepository.findIdempotencyForUpdate(10, 20, "INVENTORY_SUPPLIER_RETURN", "return-key-1"))
                .thenAnswer(invocation -> Optional.of(new DbInventoryAdjustmentRepository.IdempotencyRecord(
                        1, requestHash.get(), "PENDING", null, operationId)));
    }

    private InventorySupplierReturnRequest request(int quantity,
                                                   long expectedVersion,
                                                   int refundAmount,
                                                   String idempotencyKey) {
        return new InventorySupplierReturnRequest(
                10, 20, 30, quantity, expectedVersion, refundAmount, "Damaged packaging", idempotencyKey);
    }
}
