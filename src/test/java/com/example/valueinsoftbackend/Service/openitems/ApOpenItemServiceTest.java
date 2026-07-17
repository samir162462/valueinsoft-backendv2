package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbApOpenItem;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReadModels;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApOpenItemServiceTest {

    @Test
    void supplierReceiptCanPartiallySettlePurchase() {
        DbApOpenItem repository = mock(DbApOpenItem.class);
        ApOpenItemService service = new ApOpenItemService(repository,
                mock(com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService.class));
        when(repository.findReceiptForUpdate(7, 81))
                .thenReturn(new OpenItemsReadModels.ReceiptLock(81, 3, 22, money("30"), "POSTED"));
        when(repository.sumActiveAllocationsForReceipt(7, 81)).thenReturn(BigDecimal.ZERO);
        when(repository.findAllocationsByPrefix(eq(7), anyString())).thenReturn(List.of());
        when(repository.findSettleableForUpdate(7, 3, 22, "EGP")).thenReturn(List.of(
                new OpenItemsReadModels.OpenItem(4, 7, 3, 22, "PURCHASE", 90L, "PURCHASE-90",
                        LocalDateTime.now(), LocalDateTime.now(), "EGP", money("100"), money("0"),
                        money("100"), "OPEN", null)));
        when(repository.insertAllocation(anyInt(), anyInt(), anyInt(), any(), any(), anyLong(), any(),
                anyString(), anyString(), anyString())).thenReturn(701L);

        OpenItemsWriteModels.AllocationResult result = service.allocateReceipt(
                7, 3, 22, 81, new OpenItemsWriteModels.AllocationCommand("EGP", "ap-1", List.of()), "sam");

        assertEquals(0, money("30").compareTo(result.allocatedAmount()));
        verify(repository).updateSettlement(7, 4, money("30"), money("70"), "PARTIALLY_SETTLED", "sam");
    }

    @Test
    void supplierAllocationUsesLockedOpenItemCurrencyCasing() {
        DbApOpenItem repository = mock(DbApOpenItem.class);
        ApOpenItemService service = new ApOpenItemService(repository,
                mock(com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService.class));
        when(repository.findReceiptForUpdate(7, 81))
                .thenReturn(new OpenItemsReadModels.ReceiptLock(81, 3, 22, money("30"), "POSTED"));
        when(repository.sumActiveAllocationsForReceipt(7, 81)).thenReturn(BigDecimal.ZERO);
        when(repository.findAllocationsByPrefix(eq(7), anyString())).thenReturn(List.of());
        when(repository.findOpenItemsForUpdate(7, List.of(4L))).thenReturn(List.of(
                new OpenItemsReadModels.OpenItem(4, 7, 3, 22, "PURCHASE", 90L, "PURCHASE-90",
                        LocalDateTime.now(), LocalDateTime.now(), "le", money("100"), money("0"),
                        money("100"), "OPEN", null)));
        when(repository.insertAllocation(anyInt(), anyInt(), anyInt(), any(), any(), anyLong(), any(),
                anyString(), anyString(), anyString())).thenReturn(701L);

        service.allocateReceipt(7, 3, 22, 81,
                new OpenItemsWriteModels.AllocationCommand("LE", "legacy-currency-case",
                        List.of(new OpenItemsWriteModels.AllocationTarget(4, money("30")))),
                "sam");

        verify(repository).insertAllocation(eq(7), eq(3), eq(22), eq(81), isNull(),
                eq(4L), eq(money("30")), eq("le"), anyString(), eq("sam"));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
