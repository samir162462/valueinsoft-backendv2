package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Response.CreateOrderResult;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.openitems.ArOpenItemService;
import com.example.valueinsoftbackend.Service.client.ClientReceiptService;
import com.example.valueinsoftbackend.Service.openitems.SupplierReceivableService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PosSaleOpenItemsHookTest {

    @Test
    void creditSaleCreatesOpenItemInOrderWritePath() {
        DbPosOrder orders = mock(DbPosOrder.class);
        DbPosShiftPeriod shifts = mock(DbPosShiftPeriod.class);
        FinanceOperationalPostingService finance = mock(FinanceOperationalPostingService.class);
        ArOpenItemService ar = mock(ArOpenItemService.class);
        PosSalePostingService service = new PosSalePostingService(orders, shifts, finance, null);
        service.setArOpenItemService(ar);
        Timestamp time = Timestamp.valueOf("2026-07-12 10:00:00");
        when(orders.addOrder(any(Order.class), eq(7))).thenReturn(new CreateOrderResult(44, "R", false, null, time));
        Order order = order("CREDIT", 11);

        service.postSale(7, order);

        verify(ar).createPosOrderOpenItem(7, 3, 11, 44, BigDecimal.valueOf(100),
                time.toLocalDateTime(), "order-key", "sam");
    }

    @Test
    void walkInCreditSaleIsRejected() {
        DbPosOrder orders = mock(DbPosOrder.class);
        PosSalePostingService service = new PosSalePostingService(
                orders, mock(DbPosShiftPeriod.class), mock(FinanceOperationalPostingService.class), null);
        service.setArOpenItemService(mock(ArOpenItemService.class));
        when(orders.addOrder(any(Order.class), eq(7))).thenReturn(new CreateOrderResult(
                44, "R", false, null, Timestamp.valueOf("2026-07-12 10:00:00")));

        ApiException error = assertThrows(ApiException.class, () -> service.postSale(7, order("CREDIT", 0)));
        assertEquals("CREDIT_SALE_CLIENT_REQUIRED", error.getCode());
    }

    @Test
    void partialCreditSaleCreatesFullOpenItemAndAllocatesCashReceivedNow() {
        DbPosOrder orders = mock(DbPosOrder.class);
        DbPosShiftPeriod shifts = mock(DbPosShiftPeriod.class);
        ArOpenItemService ar = mock(ArOpenItemService.class);
        ClientReceiptService receipts = mock(ClientReceiptService.class);
        PosSalePostingService service = new PosSalePostingService(
                orders, shifts, mock(FinanceOperationalPostingService.class), null);
        service.setArOpenItemService(ar);
        service.setClientReceiptService(receipts);
        Timestamp time = Timestamp.valueOf("2026-07-12 10:00:00");
        when(orders.addOrder(any(Order.class), eq(7))).thenReturn(new CreateOrderResult(44, "R", false, 9, time));
        when(ar.createPosOrderOpenItem(anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any(), any()))
                .thenReturn(501L);
        Order order = order("RECEIVABLE", 11);
        order.setPaidNowAmount(BigDecimal.valueOf(40));

        service.postSale(7, order);

        verify(ar).createPosOrderOpenItem(7, 3, 11, 44, BigDecimal.valueOf(100),
                time.toLocalDateTime(), "order-key", "sam");
        verify(receipts).recordPosCheckoutPayment(7, 3, 11, 501L, BigDecimal.valueOf(40), "order-key", "sam");
        verify(shifts).insertCashMovement(7, 9, 3, "CASH_SALE", BigDecimal.valueOf(40),
                "sam", "Sale #44", 11, null, "ORDER", "44");
    }

    @Test
    void partialSupplierSaleCreatesSupplierReceivableWithoutUsingClientId() {
        DbPosOrder orders = mock(DbPosOrder.class);
        DbPosShiftPeriod shifts = mock(DbPosShiftPeriod.class);
        SupplierReceivableService supplierReceivables = mock(SupplierReceivableService.class);
        PosSalePostingService service = new PosSalePostingService(
                orders, shifts, mock(FinanceOperationalPostingService.class), null);
        service.setArOpenItemService(mock(ArOpenItemService.class));
        service.setSupplierReceivableService(supplierReceivables);
        Timestamp time = Timestamp.valueOf("2026-07-12 10:00:00");
        when(orders.addOrder(any(Order.class), eq(7))).thenReturn(new CreateOrderResult(44, "R", false, 9, time));
        Order order = order("RECEIVABLE", 0);
        order.setReceivablePartyType("SUPPLIER");
        order.setReceivableSupplierId(77);
        order.setPaidNowAmount(BigDecimal.valueOf(40));

        service.postSale(7, order);

        verify(supplierReceivables).recordPosSupplierSale(7, 3, 77, 44, BigDecimal.valueOf(100),
                BigDecimal.valueOf(40), time.toLocalDateTime(), "order-key", "sam");
        verify(shifts).insertCashMovement(7, 9, 3, "CASH_SALE", BigDecimal.valueOf(40),
                "sam", "Sale #44", null, null, "ORDER", "44");
    }

    private static Order order(String type, int clientId) {
        Order order = new Order(0, Timestamp.valueOf("2026-07-12 10:00:00"), "Client", type,
                0, 100, "sam", 3, clientId, 20, 0, new ArrayList<>());
        order.setIdempotencyKey("order-key");
        return order;
    }
}
