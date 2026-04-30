package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMSupplierReceipt;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Request.SupplierReceiptCreateRequest;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SupplierReceiptServiceTest {

    private static final int COMPANY_ID = 1095;
    private static final int BRANCH_ID = 1074;
    private static final UUID POSTING_REQUEST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID JOURNAL_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private DBMSupplierReceipt supplierReceiptRepository;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private SupplierReceiptService supplierReceiptService;

    @BeforeEach
    void setUp() {
        supplierReceiptRepository = Mockito.mock(DBMSupplierReceipt.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        supplierReceiptService = new SupplierReceiptService(supplierReceiptRepository, financeOperationalPostingService);
    }

    @Test
    void addSupplierReceiptReturnsPostingMetadataWhenPostingRequestIsCreated() {
        SupplierReceiptCreateRequest request = buildRequest();
        SupplierReceipt created = createdReceipt();
        when(supplierReceiptRepository.createSupplierReceipt(eq(COMPANY_ID), any(SupplierReceipt.class))).thenReturn(created);
        when(supplierReceiptRepository.updateInventoryRemainingAmount(eq(COMPANY_ID), eq(BRANCH_ID), eq(7001), eq(BigDecimal.ZERO))).thenReturn(1);
        when(supplierReceiptRepository.decrementSupplierRemaining(eq(COMPANY_ID), eq(BRANCH_ID), eq(88), eq(BigDecimal.valueOf(500)))).thenReturn(1);
        when(financeOperationalPostingService.enqueueSupplierPayment(eq(COMPANY_ID), eq(created)))
                .thenReturn(postingRequest("pending", null));

        SupplierReceipt response = supplierReceiptService.addSupplierReceipt(COMPANY_ID, request);

        assertEquals(91, response.getSrId());
        assertEquals("pending", response.getPostingStatus());
        assertEquals(POSTING_REQUEST_ID, response.getPostingRequestId());
        assertEquals(JOURNAL_ID, response.getJournalId());
    }

    @Test
    void addSupplierReceiptReturnsFailedPostingMetadataWhenPostingEnqueueFails() {
        SupplierReceiptCreateRequest request = buildRequest();
        SupplierReceipt created = createdReceipt();
        when(supplierReceiptRepository.createSupplierReceipt(eq(COMPANY_ID), any(SupplierReceipt.class))).thenReturn(created);
        when(supplierReceiptRepository.updateInventoryRemainingAmount(eq(COMPANY_ID), eq(BRANCH_ID), eq(7001), eq(BigDecimal.ZERO))).thenReturn(1);
        when(supplierReceiptRepository.decrementSupplierRemaining(eq(COMPANY_ID), eq(BRANCH_ID), eq(88), eq(BigDecimal.valueOf(500)))).thenReturn(1);
        when(financeOperationalPostingService.enqueueSupplierPayment(eq(COMPANY_ID), eq(created)))
                .thenThrow(new RuntimeException("No open fiscal period"));

        SupplierReceipt response = supplierReceiptService.addSupplierReceipt(COMPANY_ID, request);

        assertEquals(91, response.getSrId());
        assertEquals("failed", response.getPostingStatus());
        assertEquals("No open fiscal period", response.getPostingFailureReason());
    }

    private SupplierReceiptCreateRequest buildRequest() {
        SupplierReceiptCreateRequest request = new SupplierReceiptCreateRequest();
        request.setTransId(7001);
        request.setAmountPaid(BigDecimal.valueOf(500));
        request.setRemainingAmount(BigDecimal.ZERO);
        request.setUserRecived("sam");
        request.setSupplierId(88);
        request.setType("ReceiveVMoney");
        request.setBranchId(BRANCH_ID);
        return request;
    }

    private SupplierReceipt createdReceipt() {
        return new SupplierReceipt(
                91,
                7001,
                BigDecimal.valueOf(500),
                BigDecimal.ZERO,
                Timestamp.valueOf("2026-07-11 16:10:00"),
                "sam",
                88,
                "ReceiveVMoney",
                BRANCH_ID
        );
    }

    private FinancePostingRequestItem postingRequest(String status, String lastError) {
        FinancePostingRequestItem item = new FinancePostingRequestItem();
        item.setPostingRequestId(POSTING_REQUEST_ID);
        item.setCompanyId(COMPANY_ID);
        item.setBranchId(BRANCH_ID);
        item.setSourceModule("payment");
        item.setSourceType("supplier_payment");
        item.setSourceId("supplier-receipt-91");
        item.setPostingDate(LocalDate.of(2026, 7, 11));
        item.setStatus(status);
        item.setJournalEntryId(JOURNAL_ID);
        item.setLastError(lastError);
        item.setCreatedAt(Instant.parse("2026-07-11T13:10:00Z"));
        assertNotNull(item.getPostingRequestId());
        return item;
    }
}
