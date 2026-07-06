package com.example.valueinsoftbackend.Service.client;

import com.example.valueinsoftbackend.DatabaseRequests.DbClientTradeIn;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.ClientTradeInPaymentRequest;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pay Client flow: idempotent, FIFO allocation against open trade-in
 * receipts, correct remaining payable, and 409 on mismatched retries.
 */
class ClientTradeInServicePaymentTest {

    private static final int COMPANY_ID = 7;

    private DbClientTradeIn dbClientTradeIn;
    private FinanceOperationalPostingService financePostingService;
    private ClientTradeInService service;

    @BeforeEach
    void setUp() {
        dbClientTradeIn = mock(DbClientTradeIn.class);
        financePostingService = mock(FinanceOperationalPostingService.class);
        service = new ClientTradeInService(dbClientTradeIn, financePostingService);
    }

    private ClientTradeInPaymentRequest request(BigDecimal amount) {
        ClientTradeInPaymentRequest req = new ClientTradeInPaymentRequest();
        req.setBranchId(10000);
        req.setClientId(77);
        req.setAmount(amount);
        req.setPaymentMethod("cash");
        req.setIdempotencyKey("pay-key-1");
        return req;
    }

    private DbClientTradeIn.TradeInReceiptRow openReceipt(long id, String remaining) {
        return new DbClientTradeIn.TradeInReceiptRow(
                id, 10000, null, 500 + id, 5, null, null, null, "ref-" + id, 1,
                "USED", null, new BigDecimal("100.0000"), new BigDecimal(remaining),
                BigDecimal.ZERO, new BigDecimal(remaining), "UNPAID", null, "POSTED",
                new Timestamp(System.currentTimeMillis() - id), null, null);
    }

    private void stubActiveClient() {
        when(dbClientTradeIn.findClientForUpdate(COMPANY_ID, 77))
                .thenReturn(Optional.of(new DbClientTradeIn.ClientRow(77, "Ahmed", "0100", "ACTIVE")));
    }

    @Test
    void paymentAllocatesFifoAcrossOpenReceipts() {
        stubActiveClient();
        when(dbClientTradeIn.findPaymentByIdempotencyKey(eq(COMPANY_ID), anyString())).thenReturn(Optional.empty());
        when(dbClientTradeIn.listOpenReceiptsForUpdate(COMPANY_ID, 77))
                .thenReturn(List.of(openReceipt(1, "300.0000"), openReceipt(2, "200.0000")));
        when(dbClientTradeIn.insertPayment(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(9L);
        when(dbClientTradeIn.applyPaymentToReceipt(anyInt(), anyLong(), any(), any())).thenReturn(1);
        when(dbClientTradeIn.summarize(COMPANY_ID, 77))
                .thenReturn(new DbClientTradeIn.TradeInSummary(2,
                        new BigDecimal("500.0000"), new BigDecimal("400.0000"), new BigDecimal("100.0000")));

        Map<String, Object> result = service.payClient(COMPANY_ID, "cashier", request(new BigDecimal("400")));

        assertEquals(9L, result.get("paymentId"));
        assertEquals(Boolean.FALSE, result.get("idempotentReplay"));
        verify(dbClientTradeIn).applyPaymentToReceipt(eq(COMPANY_ID), eq(1L), eq(new BigDecimal("300.0000")), any());
        verify(dbClientTradeIn).applyPaymentToReceipt(eq(COMPANY_ID), eq(2L), eq(new BigDecimal("100.0000")), any());
        verify(dbClientTradeIn).insertAllocation(COMPANY_ID, 9L, 1L, new BigDecimal("300.0000"));
        verify(dbClientTradeIn).insertAllocation(COMPANY_ID, 9L, 2L, new BigDecimal("100.0000"));
        verify(financePostingService).enqueueClientTradeInPayment(
                eq(COMPANY_ID), eq(10000), eq(77), eq(9L), eq(new BigDecimal("400.0000")), eq("cash"), any(), any());
        assertEquals(0, new BigDecimal("100.0000").compareTo((BigDecimal) result.get("remainingPayable")));
    }

    @Test
    void paymentExceedingOutstandingPayableIsRejected() {
        stubActiveClient();
        when(dbClientTradeIn.findPaymentByIdempotencyKey(eq(COMPANY_ID), anyString())).thenReturn(Optional.empty());
        when(dbClientTradeIn.listOpenReceiptsForUpdate(COMPANY_ID, 77))
                .thenReturn(List.of(openReceipt(1, "300.0000")));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.payClient(COMPANY_ID, "cashier", request(new BigDecimal("400"))));
        assertEquals("PAYMENT_EXCEEDS_PAYABLE", exception.getCode());
        verify(dbClientTradeIn, never()).insertPayment(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void duplicateRetryWithSamePayloadReplaysWithoutSideEffects() {
        ClientTradeInPaymentRequest req = request(new BigDecimal("400"));
        // Hash of the same canonical payload the service would compute.
        String storedHash = serviceHash(req);
        when(dbClientTradeIn.findPaymentByIdempotencyKey(COMPANY_ID, "pay-key-1"))
                .thenReturn(Optional.of(new DbClientTradeIn.PaymentRow(
                        9L, 10000, 77, new BigDecimal("400.0000"), "cash", null,
                        "pay-key-1", storedHash, "POSTED", null, "cashier",
                        new Timestamp(System.currentTimeMillis()))));
        when(dbClientTradeIn.summarize(COMPANY_ID, 77))
                .thenReturn(new DbClientTradeIn.TradeInSummary(2,
                        new BigDecimal("500.0000"), new BigDecimal("400.0000"), new BigDecimal("100.0000")));

        Map<String, Object> result = service.payClient(COMPANY_ID, "cashier", req);

        assertEquals(Boolean.TRUE, result.get("idempotentReplay"));
        assertEquals(9L, result.get("paymentId"));
        verify(dbClientTradeIn, never()).insertPayment(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyString(), anyString(), any());
        verify(financePostingService, never()).enqueueClientTradeInPayment(anyInt(), anyInt(), anyInt(), anyLong(), any(), anyString(), any(), any());
    }

    @Test
    void duplicateRetryWithDifferentPayloadReturnsConflict() {
        when(dbClientTradeIn.findPaymentByIdempotencyKey(COMPANY_ID, "pay-key-1"))
                .thenReturn(Optional.of(new DbClientTradeIn.PaymentRow(
                        9L, 10000, 77, new BigDecimal("999.0000"), "cash", null,
                        "pay-key-1", "another-hash-entirely", "POSTED", null, "cashier",
                        new Timestamp(System.currentTimeMillis()))));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.payClient(COMPANY_ID, "cashier", request(new BigDecimal("400"))));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("IDEMPOTENCY_KEY_PAYLOAD_CONFLICT", exception.getCode());
    }

    @Test
    void archivedClientCannotBePaid() {
        when(dbClientTradeIn.findPaymentByIdempotencyKey(eq(COMPANY_ID), anyString())).thenReturn(Optional.empty());
        when(dbClientTradeIn.findClientForUpdate(COMPANY_ID, 77))
                .thenReturn(Optional.of(new DbClientTradeIn.ClientRow(77, "Ahmed", "0100", "ARCHIVED")));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.payClient(COMPANY_ID, "cashier", request(new BigDecimal("100"))));
        assertEquals("CLIENT_NOT_ACTIVE", exception.getCode());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    /**
     * Recomputes the canonical hash exactly like ClientTradeInService so the
     * replay test uses a genuinely matching stored hash.
     */
    private String serviceHash(ClientTradeInPaymentRequest req) {
        String canonical = COMPANY_ID + "|" + req.getBranchId() + "|" + req.getClientId() + "|"
                + req.getAmount().setScale(4).toPlainString() + "|" + req.getPaymentMethod().toLowerCase() + "|";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hashed) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
