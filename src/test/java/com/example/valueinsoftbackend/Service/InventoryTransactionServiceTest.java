package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosInventoryTransaction;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.CreateInventoryTransactionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryTransactionServiceTest {

    private DbPosInventoryTransaction dbPosInventoryTransaction;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private InventoryTransactionService service;

    @BeforeEach
    void setUp() {
        dbPosInventoryTransaction = Mockito.mock(DbPosInventoryTransaction.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        service = new InventoryTransactionService(dbPosInventoryTransaction, financeOperationalPostingService);

        when(dbPosInventoryTransaction.insertInventoryTransaction(any(), anyInt(), anyInt()))
                .thenReturn(new DbPosInventoryTransaction.AddInventoryTransactionResult(7001));
        when(dbPosInventoryTransaction.updateSupplierTotals(anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);
        when(dbPosInventoryTransaction.syncLatestLedgerMetadata(anyInt(), anyInt(), any(InventoryTransaction.class)))
                .thenReturn(1);
        when(dbPosInventoryTransaction.findLatestAdjustmentInventoryMovementId(anyInt(), anyInt(), any(InventoryTransaction.class)))
                .thenReturn(99002L);
    }

    @Test
    void addTransactionRoutesExplicitSupplierReturnToPurchaseReturnPosting() {
        service.addTransaction(request("Supplier Return", -2, -700, 100));

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(financeOperationalPostingService).enqueuePurchaseReturnInventoryTransaction(
                Mockito.eq(7),
                Mockito.eq(3),
                transactionCaptor.capture(),
                Mockito.eq(7001),
                Mockito.eq(99002L));
        verify(financeOperationalPostingService, never()).enqueueInventoryAdjustmentTransaction(anyInt(), anyInt(), any(), anyInt(), any());

        InventoryTransaction captured = transactionCaptor.getValue();
        assertEquals("Supplier Return", captured.getTransactionType());
        assertEquals(-2, captured.getNumItems());
        assertEquals(-700, captured.getTransTotal());
    }

    @Test
    void addTransactionKeepsGenericNegativeUpdateAsInventoryAdjustment() {
        service.addTransaction(request("Update", -2, -700, 0));

        verify(financeOperationalPostingService).enqueueInventoryAdjustmentTransaction(
                Mockito.eq(7),
                Mockito.eq(3),
                any(InventoryTransaction.class),
                Mockito.eq(7001),
                Mockito.eq(99002L));
        verify(financeOperationalPostingService, never()).enqueuePurchaseReturnInventoryTransaction(anyInt(), anyInt(), any(), anyInt(), any());
    }

    private CreateInventoryTransactionRequest request(String transactionType,
                                                     int quantity,
                                                     int transTotal,
                                                     int remainingAmount) {
        CreateInventoryTransactionRequest request = new CreateInventoryTransactionRequest();
        request.setCompanyId(7);
        request.setBranchId(3);
        request.setProductId(41);
        request.setUserName("sam");
        request.setSupplierId(88);
        request.setTransactionType(transactionType);
        request.setNumItems(quantity);
        request.setTransTotal(transTotal);
        request.setPayType("Cash");
        request.setTime("2026-07-07 10:05:00.000");
        request.setRemainingAmount(remainingAmount);
        return request;
    }
}
