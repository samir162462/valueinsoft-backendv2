package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReadModels;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArOpenItemServiceTest {

    private DbArOpenItem repository;
    private ArOpenItemService service;

    @BeforeEach
    void setUp() {
        repository = mock(DbArOpenItem.class);
        service = new ArOpenItemService(repository,
                mock(com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService.class));
        when(repository.findReceiptForUpdate(7, 91))
                .thenReturn(new OpenItemsReadModels.ReceiptLock(91, 3, 11, money("100"), "POSTED"));
        when(repository.sumActiveAllocationsForReceipt(7, 91)).thenReturn(BigDecimal.ZERO);
        when(repository.findAllocationsByPrefix(eq(7), anyString())).thenReturn(List.of());
        when(repository.insertAllocation(anyInt(), anyInt(), anyInt(), any(), any(), anyLong(),
                any(), anyString(), anyString(), anyString())).thenReturn(501L, 502L);
    }

    @Test
    void fifoAllocationSettlesOldestDueItemsAcrossDocuments() {
        OpenItemsReadModels.OpenItem oldest = item(2, "60", "0", "60");
        OpenItemsReadModels.OpenItem newer = item(1, "80", "0", "80");
        when(repository.findSettleableForUpdate(7, 3, 11, "EGP")).thenReturn(List.of(oldest, newer));

        OpenItemsWriteModels.AllocationResult result = service.allocateReceipt(
                7, 3, 11, 91, new OpenItemsWriteModels.AllocationCommand("EGP", "fifo-1", List.of()), "sam");

        assertEquals(0, money("100").compareTo(result.allocatedAmount()));
        assertEquals(2, result.allocations().size());
        InOrder order = inOrder(repository);
        order.verify(repository).insertAllocation(eq(7), eq(3), eq(11), eq(91), isNull(),
                eq(2L), eq(money("60")), eq("EGP"), anyString(), eq("sam"));
        order.verify(repository).updateSettlement(7, 2, money("60"), money("0"), "SETTLED", "sam");
        order.verify(repository).insertAllocation(eq(7), eq(3), eq(11), eq(91), isNull(),
                eq(1L), eq(money("40")), eq("EGP"), anyString(), eq("sam"));
    }

    @Test
    void explicitOverAllocationIsRejected() {
        when(repository.findOpenItemsForUpdate(7, List.of(1L))).thenReturn(List.of(item(1, "50", "0", "50")));
        var command = new OpenItemsWriteModels.AllocationCommand("EGP", "over-1",
                List.of(new OpenItemsWriteModels.AllocationTarget(1, money("60"))));

        ApiException error = assertThrows(ApiException.class,
                () -> service.allocateReceipt(7, 3, 11, 91, command, "sam"));

        assertEquals("OPEN_ITEMS_OVER_ALLOCATION", error.getCode());
        verify(repository, never()).insertAllocation(anyInt(), anyInt(), anyInt(), any(), any(), anyLong(),
                any(), anyString(), anyString(), anyString());
    }

    @Test
    void explicitAllocationUsesLockedOpenItemCurrencyCasing() {
        when(repository.findOpenItemsForUpdate(7, List.of(1L)))
                .thenReturn(List.of(item(1, "50", "0", "50", "le")));

        service.allocateReceipt(7, 3, 11, 91,
                new OpenItemsWriteModels.AllocationCommand("LE", "legacy-currency-case",
                        List.of(new OpenItemsWriteModels.AllocationTarget(1, money("25")))),
                "sam");

        verify(repository).insertAllocation(eq(7), eq(3), eq(11), eq(91), isNull(),
                eq(1L), eq(money("25")), eq("le"), anyString(), eq("sam"));
    }

    @Test
    void idempotentReplayReturnsExistingAllocationsWithoutWrites() {
        OpenItemsWriteModels.AllocationRow row = new OpenItemsWriteModels.AllocationRow(
                501, 91, null, 1, 3, 11, "EGP", money("25"), "POSTED", null, "key");
        when(repository.findAllocationsByPrefix(eq(7), anyString())).thenReturn(List.of(row));

        OpenItemsWriteModels.AllocationResult result = service.allocateReceipt(
                7, 3, 11, 91, new OpenItemsWriteModels.AllocationCommand("EGP", "same", List.of()), "sam");

        assertTrue(result.idempotencyReplay());
        assertEquals(0, money("25").compareTo(result.allocatedAmount()));
        verify(repository, never()).findSettleableForUpdate(anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    void reversalWritesMirrorBeforeMutatingOriginalAndReopeningItem() {
        OpenItemsWriteModels.AllocationRow allocation = new OpenItemsWriteModels.AllocationRow(
                501, 91, null, 1, 3, 11, "EGP", money("40"), "POSTED", null, "key");
        when(repository.findAllocationForUpdate(7, 501)).thenReturn(allocation);
        when(repository.findOpenItemsForUpdate(7, List.of(1L)))
                .thenReturn(List.of(item(1, "100", "40", "60")));
        when(repository.insertAllocationReversal(eq(7), eq(allocation), anyString(), eq("sam"))).thenReturn(601L);

        service.reverseAllocation(7, 501, "reverse-1", "sam");

        InOrder order = inOrder(repository);
        order.verify(repository).insertAllocationReversal(eq(7), eq(allocation), anyString(), eq("sam"));
        order.verify(repository).markAllocationReversed(7, 501);
        order.verify(repository).updateSettlement(7, 1, money("0"), money("100"), "OPEN", "sam");
    }

    @Test
    void receiptCannotReverseBeforeItsAllocations() {
        when(repository.countActiveAllocationsForReceipt(7, 91)).thenReturn(1);
        ApiException error = assertThrows(ApiException.class, () -> service.reverseReceipt(7, 91, "test", "sam"));
        assertEquals("OPEN_ITEMS_REVERSE_ALLOCATIONS_FIRST", error.getCode());
    }

    private static OpenItemsReadModels.OpenItem item(long id, String total, String settled, String remaining) {
        return item(id, total, settled, remaining, "EGP");
    }

    private static OpenItemsReadModels.OpenItem item(long id, String total, String settled, String remaining,
                                                      String currencyCode) {
        return new OpenItemsReadModels.OpenItem(id, 7, 3, 11, "POS_ORDER", id, "POS-" + id,
                LocalDateTime.now(), LocalDateTime.now().plusDays(id), currencyCode, money(total), money(settled),
                money(remaining), new BigDecimal(remaining).signum() == 0 ? "SETTLED" :
                new BigDecimal(settled).signum() == 0 ? "OPEN" : "PARTIALLY_SETTLED", null);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
