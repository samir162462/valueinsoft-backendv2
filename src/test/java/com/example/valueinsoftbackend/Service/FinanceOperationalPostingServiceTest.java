package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceOperationalPostingServiceTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000006001");

    private DbFinanceSetup dbFinanceSetup;
    private FinancePostingRequestService financePostingRequestService;
    private FinanceOperationalPostingService service;

    @BeforeEach
    void setUp() {
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        financePostingRequestService = Mockito.mock(FinancePostingRequestService.class);
        service = new FinanceOperationalPostingService(dbFinanceSetup, financePostingRequestService);
    }

    @Test
    void enqueuePosSaleBuildsPendingPostingRequestFromSavedOrder() {
        Timestamp orderTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 5, 11, 30));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, orderTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueuePosSale(
                COMPANY_ID,
                order(),
                9001,
                orderTime,
                List.of(new DbPosOrder.OrderFinanceCostLine(
                        41,
                        2,
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(600))));

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals(COMPANY_ID, request.getCompanyId());
        assertEquals(BRANCH_ID, request.getBranchId());
        assertEquals("pos", request.getSourceModule());
        assertEquals("sale", request.getSourceType());
        assertEquals("order-9001", request.getSourceId());
        assertEquals(FISCAL_PERIOD_ID, request.getFiscalPeriodId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals("EGP", payload.get("currencyCode"));
        assertEquals(BigDecimal.valueOf(1200).setScale(4), payload.get("netAmount"));
        assertEquals(BigDecimal.valueOf(100).setScale(4), payload.get("discountAmount"));
        assertEquals("Dirict", payload.get("paymentMethod"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertEquals(1, items.size());
        assertEquals(41, items.get(0).get("productId"));
        assertEquals(BigDecimal.valueOf(600).setScale(4), items.get(0).get("totalCost"));
    }

    @Test
    void enqueuePosSaleRequiresOpenPostingPeriodForOrderDate() {
        Timestamp orderTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 5, 11, 30));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, orderTime.toLocalDateTime().toLocalDate()))
                .thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.enqueuePosSale(COMPANY_ID, order(), 9001, orderTime, List.of()));

        assertEquals("FINANCE_POSTING_PERIOD_NOT_FOUND", exception.getCode());
        verify(financePostingRequestService, never()).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enqueuePosSaleReturnBuildsPendingPostingRequestFromBounceBack() {
        Timestamp returnTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 5, 12, 15));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, returnTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueuePosSaleReturn(
                COMPANY_ID,
                BRANCH_ID,
                bounceBackContext(),
                1100,
                true,
                99004L,
                8003L,
                returnTime);

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("pos", request.getSourceModule());
        assertEquals("sale_return", request.getSourceType());
        assertEquals("order-detail-77", request.getSourceId());
        assertEquals(FISCAL_PERIOD_ID, request.getFiscalPeriodId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(9001, payload.get("originalOrderId"));
        assertEquals(77, payload.get("orderDetailId"));
        assertEquals(55, payload.get("customerId"));
        assertEquals(BigDecimal.valueOf(1100).setScale(4), payload.get("refundAmount"));
        assertEquals(BigDecimal.valueOf(1100).setScale(4), payload.get("salesReturnAmount"));
        assertEquals("cash", payload.get("refundMethod"));
        assertEquals(true, payload.get("returnedToStock"));
        assertEquals("cash-movement-8003", payload.get("paymentId"));
        assertEquals(99004L, payload.get("inventoryMovementId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertEquals(1, items.size());
        assertEquals(41, items.get(0).get("productId"));
        assertEquals(BigDecimal.valueOf(600).setScale(4), items.get(0).get("totalCost"));
    }

    @Test
    void enqueuePurchaseInventoryTransactionBuildsPendingPostingRequest() {
        Timestamp transactionTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 6, 13, 15));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, transactionTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueuePurchaseInventoryTransaction(
                COMPANY_ID,
                BRANCH_ID,
                inventoryTransaction(transactionTime),
                7001,
                99001L);

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("purchase", request.getSourceModule());
        assertEquals("purchase_invoice", request.getSourceType());
        assertEquals("inventory-transaction-7001", request.getSourceId());
        assertEquals(FISCAL_PERIOD_ID, request.getFiscalPeriodId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(88, payload.get("supplierId"));
        assertEquals(BigDecimal.valueOf(1500).setScale(4), payload.get("inventoryAmount"));
        assertEquals(BigDecimal.valueOf(1200).setScale(4), payload.get("paidAmount"));
        assertEquals("Partial", payload.get("paymentMethod"));
        assertEquals(99001L, payload.get("inventoryMovementId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertEquals(1, items.size());
        assertEquals(41, items.get(0).get("productId"));
        assertEquals(BigDecimal.valueOf(1500).setScale(4), items.get(0).get("totalCost"));
    }

    @Test
    void enqueuePurchaseInventoryTransactionRequiresOpenPostingPeriod() {
        Timestamp transactionTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 6, 13, 15));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, transactionTime.toLocalDateTime().toLocalDate()))
                .thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.enqueuePurchaseInventoryTransaction(
                        COMPANY_ID,
                        BRANCH_ID,
                        inventoryTransaction(transactionTime),
                        7001,
                        99001L));

        assertEquals("FINANCE_POSTING_PERIOD_NOT_FOUND", exception.getCode());
        verify(financePostingRequestService, never()).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enqueuePurchaseReturnInventoryTransactionBuildsPendingPostingRequest() {
        Timestamp transactionTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 6, 14, 40));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, transactionTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        InventoryTransaction transaction = new InventoryTransaction(
                0,
                41,
                "sam",
                88,
                "Supplier Return",
                -3,
                -1500,
                "Cash",
                transactionTime,
                300);

        service.enqueuePurchaseReturnInventoryTransaction(
                COMPANY_ID,
                BRANCH_ID,
                transaction,
                7003,
                99011L);

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("purchase", request.getSourceModule());
        assertEquals("purchase_return", request.getSourceType());
        assertEquals("inventory-transaction-7003", request.getSourceId());
        assertEquals(FISCAL_PERIOD_ID, request.getFiscalPeriodId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(88, payload.get("supplierId"));
        assertEquals(BigDecimal.valueOf(1500).setScale(4), payload.get("inventoryAmount"));
        assertEquals(BigDecimal.valueOf(1200).setScale(4), payload.get("refundedAmount"));
        assertEquals("Cash", payload.get("paymentMethod"));
        assertEquals("Supplier Return", payload.get("returnReason"));
        assertEquals(99011L, payload.get("inventoryMovementId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertEquals(1, items.size());
        assertEquals(41, items.get(0).get("productId"));
        assertEquals(3, items.get(0).get("quantity"));
        assertEquals(BigDecimal.valueOf(1500).setScale(4), items.get(0).get("totalCost"));
    }

    @Test
    void enqueueInventoryAdjustmentTransactionBuildsDecreasePostingRequest() {
        Timestamp transactionTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 7, 10, 5));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, transactionTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        InventoryTransaction transaction = new InventoryTransaction(
                0,
                41,
                "sam",
                88,
                "Update",
                -2,
                -700,
                "Adjustment",
                transactionTime,
                0);

        service.enqueueInventoryAdjustmentTransaction(COMPANY_ID, BRANCH_ID, transaction, 7002, 99002L);

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("inventory", request.getSourceModule());
        assertEquals("inventory_adjustment", request.getSourceType());
        assertEquals("inventory-transaction-7002", request.getSourceId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals("decrease", payload.get("direction"));
        assertEquals(-2, payload.get("quantityDelta"));
        assertEquals(BigDecimal.valueOf(700).setScale(4), payload.get("adjustmentAmount"));
        assertEquals("manual_inventory_transaction", payload.get("reasonCode"));
        assertEquals(99002L, payload.get("inventoryMovementId"));
    }

    @Test
    void enqueueDamagedItemBuildsDamagePostingRequest() {
        Timestamp damagedTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 8, 9, 45));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, damagedTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueueDamagedItem(
                COMPANY_ID,
                BRANCH_ID,
                damagedItem(damagedTime),
                6101,
                99003L);

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("cashier"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("inventory", request.getSourceModule());
        assertEquals("damage", request.getSourceType());
        assertEquals("damaged-item-6101", request.getSourceId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals("decrease", payload.get("direction"));
        assertEquals(-2, payload.get("quantityDelta"));
        assertEquals(BigDecimal.valueOf(450).setScale(4), payload.get("adjustmentAmount"));
        assertEquals("damage", payload.get("reasonCode"));
        assertEquals("screen cracked", payload.get("reason"));
    }

    @Test
    void enqueueBranchStockTransferBuildsCompanyLevelPostingRequest() {
        Timestamp transferTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 8, 13, 20));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, transferTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueueBranchStockTransfer(
                COMPANY_ID,
                BRANCH_ID,
                4,
                "stock-transfer-501",
                41,
                2,
                BigDecimal.valueOf(600),
                99021L,
                99022L,
                transferTime,
                "sam");

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("inventory", request.getSourceModule());
        assertEquals("stock_transfer", request.getSourceType());
        assertEquals("stock-transfer-501", request.getSourceId());
        assertEquals(null, request.getBranchId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(3, payload.get("sourceBranchId"));
        assertEquals(4, payload.get("destinationBranchId"));
        assertEquals(BigDecimal.valueOf(600).setScale(4), payload.get("transferAmount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertEquals(1, items.size());
        assertEquals(41, items.get(0).get("productId"));
        assertEquals(3, items.get(0).get("sourceBranchId"));
        assertEquals(4, items.get(0).get("destinationBranchId"));
        assertEquals(2, items.get(0).get("quantity"));
        assertEquals(BigDecimal.valueOf(600).setScale(4), items.get(0).get("totalCost"));
        assertEquals(99021L, items.get(0).get("sourceInventoryMovementId"));
        assertEquals(99022L, items.get(0).get("destinationInventoryMovementId"));
    }

    @Test
    void enqueueCashSafeDropBuildsPaymentSettlementRequest() {
        Timestamp movementTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 18, 20));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, movementTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueueCashSafeDrop(
                COMPANY_ID,
                BRANCH_ID,
                901,
                8001L,
                BigDecimal.valueOf(2500),
                movementTime,
                "sam");

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("payment", request.getSourceModule());
        assertEquals("safe_drop", request.getSourceType());
        assertEquals("cash-movement-8001", request.getSourceId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(BigDecimal.valueOf(2500).setScale(4), payload.get("grossAmount"));
        assertEquals(BigDecimal.valueOf(2500).setScale(4), payload.get("netAmount"));
        assertEquals("cash", payload.get("settlementMethod"));
        assertEquals("safe", payload.get("destination"));
        assertEquals("cash-movement-8001", payload.get("paymentId"));
        assertEquals(901, payload.get("shiftId"));
    }

    @Test
    void enqueueCashDrawerCloseBuildsPaymentSettlementRequest() {
        Timestamp movementTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 22, 0));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, movementTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueueCashDrawerClose(
                COMPANY_ID,
                BRANCH_ID,
                901,
                8002L,
                BigDecimal.valueOf(1750),
                movementTime,
                "sam");

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("payment", request.getSourceModule());
        assertEquals("cash_drawer_close", request.getSourceType());
        assertEquals("shift-close-901", request.getSourceId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(BigDecimal.valueOf(1750).setScale(4), payload.get("grossAmount"));
        assertEquals(BigDecimal.valueOf(1750).setScale(4), payload.get("netAmount"));
        assertEquals("cash", payload.get("settlementMethod"));
        assertEquals("safe", payload.get("destination"));
        assertEquals("shift-close-901", payload.get("paymentId"));
        assertEquals(8002L, payload.get("cashMovementId"));
    }

    @Test
    void enqueueClientReceiptBuildsCustomerReceiptPostingRequest() {
        Timestamp receiptTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 10, 14, 5));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, receiptTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueueClientReceipt(
                COMPANY_ID,
                new ClientReceipt(81, "Payment", BigDecimal.valueOf(350), receiptTime, "sam", 55, BRANCH_ID));

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("payment", request.getSourceModule());
        assertEquals("customer_receipt", request.getSourceType());
        assertEquals("client-receipt-81", request.getSourceId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(55, payload.get("customerId"));
        assertEquals(BigDecimal.valueOf(350).setScale(4), payload.get("amount"));
        assertEquals("cash", payload.get("paymentMethod"));
        assertEquals("client-receipt-81", payload.get("paymentId"));
    }

    @Test
    void enqueueSupplierPaymentBuildsSupplierPaymentPostingRequest() {
        Timestamp receiptTime = Timestamp.valueOf(LocalDateTime.of(2026, 7, 11, 16, 10));
        when(dbFinanceSetup.findPostingFiscalPeriodIdForDate(COMPANY_ID, receiptTime.toLocalDateTime().toLocalDate()))
                .thenReturn(FISCAL_PERIOD_ID);

        service.enqueueSupplierPayment(
                COMPANY_ID,
                new SupplierReceipt(91, 7001, BigDecimal.valueOf(500), BigDecimal.ZERO, receiptTime, "sam", 88, "ReceiveVMoney", BRANCH_ID));

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        verify(financePostingRequestService).createPostingRequestFromSystem(
                org.mockito.ArgumentMatchers.eq("sam"),
                requestCaptor.capture());

        FinancePostingRequestCreateRequest request = requestCaptor.getValue();
        assertEquals("payment", request.getSourceModule());
        assertEquals("supplier_payment", request.getSourceType());
        assertEquals("supplier-receipt-91", request.getSourceId());

        Map<String, Object> payload = request.getRequestPayload();
        assertEquals(88, payload.get("supplierId"));
        assertEquals(BigDecimal.valueOf(500).setScale(4), payload.get("amountPaid"));
        assertEquals("cash", payload.get("paymentMethod"));
        assertEquals("supplier-receipt-91", payload.get("paymentId"));
        assertEquals(7001, payload.get("inventoryTransactionId"));
    }

    private Order order() {
        return new Order(
                0,
                null,
                "Walk-in",
                "Dirict",
                100,
                1200,
                "sam",
                BRANCH_ID,
                55,
                500,
                0,
                new ArrayList<>());
    }

    private InventoryTransaction inventoryTransaction(Timestamp transactionTime) {
        return new InventoryTransaction(
                0,
                41,
                "sam",
                88,
                "Add",
                3,
                1500,
                "Partial",
                transactionTime,
                300);
    }

    private DamagedItem damagedItem(Timestamp damagedTime) {
        return new DamagedItem(
                0,
                41,
                "Phone",
                damagedTime,
                "screen cracked",
                "manager",
                "cashier",
                450,
                false,
                BRANCH_ID,
                2);
    }

    private DbPosOrder.OrderBounceBackContext bounceBackContext() {
        return new DbPosOrder.OrderBounceBackContext(
                77,
                9001,
                2,
                1000,
                41,
                0,
                "sam",
                55,
                100,
                300,
                false);
    }
}
