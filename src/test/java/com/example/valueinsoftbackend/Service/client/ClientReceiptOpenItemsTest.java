package com.example.valueinsoftbackend.Service.client;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMClientReceipt;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.CreateClientReceiptRequest;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.openitems.ArOpenItemService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientReceiptOpenItemsTest {

    @Test
    void positiveReceiptIsAllocatedThroughEngine() {
        DBMClientReceipt repository = mock(DBMClientReceipt.class);
        FinanceOperationalPostingService finance = mock(FinanceOperationalPostingService.class);
        ArOpenItemService ar = mock(ArOpenItemService.class);
        ClientReceiptService service = new ClientReceiptService(repository, finance, ar);
        CreateClientReceiptRequest request = request(new BigDecimal("25"), "ReceiveVMoney");
        ClientReceipt created = new ClientReceipt(91, "ReceiveVMoney", new BigDecimal("25"),
                Timestamp.valueOf("2026-07-12 10:00:00"), "sam", 11, 3);
        when(repository.createClientReceipt(eq(7), any(ClientReceipt.class))).thenReturn(created);
        when(ar.companyCurrency(7)).thenReturn("EGP");

        ClientReceipt result = service.addClientReceipt(7, request);

        assertSame(created, result);
        verify(ar).allocateReceipt(eq(7), eq(3), eq(11), eq(91), any(), eq("sam"));
        verify(finance).enqueueClientReceipt(7, created);
    }

    @Test
    void negativeLegacyStyleReceiptIsRejected() {
        ClientReceiptService service = new ClientReceiptService(
                mock(DBMClientReceipt.class), mock(FinanceOperationalPostingService.class), mock(ArOpenItemService.class));
        ApiException error = assertThrows(ApiException.class,
                () -> service.addClientReceipt(7, request(new BigDecimal("-1"), "supportExChange")));
        assertEquals("CLIENT_RECEIPT_INVALID_AMOUNT", error.getCode());
    }

    private static CreateClientReceiptRequest request(BigDecimal amount, String type) {
        CreateClientReceiptRequest request = new CreateClientReceiptRequest();
        request.setAmount(amount);
        request.setType(type);
        request.setUserName("sam");
        request.setClientId(11);
        request.setBranchId(3);
        return request;
    }
}
