package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymobWebhookSettlementResponse;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.Service.PayMobService;
import com.example.valueinsoftbackend.Service.payment.PaymentAttemptService;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderFinanceIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

class BillingPaymobWebhookSettlementServiceTest {

    private DbBillingWriteModels dbBillingWriteModels;
    private PayMobService payMobService;
    private PaymentAttemptService paymentAttemptService;
    private BillingEntitlementService billingEntitlementService;
    private PaymentProviderFinanceIntegrationService financeIntegrationService;
    private BillingPaymobWebhookSettlementService service;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        payMobService = Mockito.mock(PayMobService.class);
        paymentAttemptService = Mockito.mock(PaymentAttemptService.class);
        billingEntitlementService = Mockito.mock(BillingEntitlementService.class);
        financeIntegrationService = Mockito.mock(PaymentProviderFinanceIntegrationService.class);
        service = new BillingPaymobWebhookSettlementService(
                dbBillingWriteModels,
                payMobService,
                paymentAttemptService,
                billingEntitlementService,
                financeIntegrationService,
                new ObjectMapper()
        );
    }

    @Test
    void successfulWebhookCreatesPaymobPaymentAllocationAndActivatesEntitlement() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(true);
        when(dbBillingWriteModels.lockPaymentAttemptValidationContext("paymob", "123456"))
                .thenReturn(attempt("CHECKOUT_REQUESTED"));
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L))
                .thenReturn(invoice("300.00", "200.00"));
        when(dbBillingWriteModels.createBillingPayment(
                eq(10), eq(101L), eq("PAYMOB"), eq("paymob"), eq(new BigDecimal("300.00")),
                eq("EGP"), eq("ALLOCATED"), eq("777"), eq("provider-callback:paymob:777"), anyString()
        )).thenReturn(41L);
        when(dbBillingWriteModels.createBillingPaymentAllocation(41L, 900L, new BigDecimal("300.00"), "EGP"))
                .thenReturn(42L);

        BillingPaymobWebhookSettlementResponse response = service.settleTransactionCallback(request);

        assertEquals("SETTLED", response.getStatus());
        assertEquals(41L, response.getBillingPaymentId());
        assertEquals(42L, response.getBillingPaymentAllocationId());
        assertFalse(response.isDuplicate());
        InOrder inOrder = inOrder(paymentAttemptService, dbBillingWriteModels);
        inOrder.verify(paymentAttemptService).markSucceeded(eq("paymob"), eq("123456"), anyString(), eq("777"));
        inOrder.verify(dbBillingWriteModels).createBillingPayment(
                eq(10), eq(101L), eq("PAYMOB"), eq("paymob"), eq(new BigDecimal("300.00")),
                eq("EGP"), eq("ALLOCATED"), eq("777"), eq("provider-callback:paymob:777"), anyString()
        );
        verify(dbBillingWriteModels).updateInvoicePaymentProjection(
                eq(900L),
                eq("paid"),
                eq(new BigDecimal("500.00")),
                eq(new BigDecimal("0.00")),
                any(Instant.class),
                anyString()
        );
        verify(paymentAttemptService).markSucceeded(eq("paymob"), eq("123456"), anyString(), eq("777"));
        verify(dbBillingWriteModels).updateBranchSubscriptionStatusById(eq(1001L), eq("active"), anyString());
        verify(billingEntitlementService).recordManualStateChange(eq(55), eq(1001L), eq(900L), eq("pending_payment"), eq("active"), eq("paymob_payment_success"), anyString());
        verify(financeIntegrationService).enqueuePayMobSettlement("paymob", "123456", "777", request);
        verify(dbBillingWriteModels).markProviderEventStatus("paymob", "777", "processed", 31L, 900L, 10, null);
    }

    @Test
    void guardedAttemptUpdateFailureDoesNotCreatePaymentAllocationOrInvoiceMutation() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(true);
        when(dbBillingWriteModels.lockPaymentAttemptValidationContext("paymob", "123456"))
                .thenReturn(attempt("CHECKOUT_REQUESTED"));
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L))
                .thenReturn(invoice("300.00", "200.00"));
        doThrow(new ApiException(
                org.springframework.http.HttpStatus.CONFLICT,
                "BILLING_PAYMENT_ATTEMPT_TERMINAL_OR_NOT_FOUND",
                "Payment attempt is terminal or was not found"
        )).when(paymentAttemptService).markSucceeded(eq("paymob"), eq("123456"), anyString(), eq("777"));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.settleTransactionCallback(request));

        assertEquals("BILLING_PAYMENT_ATTEMPT_TERMINAL_OR_NOT_FOUND", exception.getCode());
        verify(dbBillingWriteModels, never()).createBillingPayment(anyInt(), any(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(dbBillingWriteModels, never()).createBillingPaymentAllocation(anyLong(), anyLong(), any(), anyString());
        verify(dbBillingWriteModels, never()).updateInvoicePaymentProjection(anyLong(), anyString(), any(), any(), any(), anyString());
        verify(financeIntegrationService, never()).enqueuePayMobSettlement(anyString(), anyString(), anyString(), any());
    }

    @Test
    void duplicateProcessedWebhookReturnsOkWithoutSettlingAgain() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(false);
        when(dbBillingWriteModels.findProviderEventStatus("paymob", "777")).thenReturn("processed");

        BillingPaymobWebhookSettlementResponse response = service.settleTransactionCallback(request);

        assertEquals("DUPLICATE_PROCESSED", response.getStatus());
        assertTrue(response.isDuplicate());
        verify(dbBillingWriteModels, never()).lockPaymentAttemptValidationContext(anyString(), anyString());
        verify(dbBillingWriteModels, never()).createBillingPayment(anyInt(), any(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void webhookAfterInvoiceAlreadyPaidIsMarkedProcessedWithoutAllocation() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(true);
        when(dbBillingWriteModels.lockPaymentAttemptValidationContext("paymob", "123456"))
                .thenReturn(attempt("CHECKOUT_REQUESTED"));
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L))
                .thenReturn(invoice("0.00", "500.00"));

        BillingPaymobWebhookSettlementResponse response = service.settleTransactionCallback(request);

        assertEquals("IGNORED_ALREADY_PAID", response.getStatus());
        verify(dbBillingWriteModels, never()).createBillingPayment(anyInt(), any(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentAttemptService, never()).markSucceeded(anyString(), anyString(), anyString(), anyString());
        verify(dbBillingWriteModels).markProviderEventStatus("paymob", "777", "processed", 31L, 900L, 10, "Invoice already paid");
    }

    @Test
    void webhookPaymentAboveRemainingDueMarksEventFailed() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(true);
        when(dbBillingWriteModels.lockPaymentAttemptValidationContext("paymob", "123456"))
                .thenReturn(attempt("CHECKOUT_REQUESTED"));
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L))
                .thenReturn(invoice("100.00", "400.00"));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.settleTransactionCallback(request));

        assertEquals("PAYMOB_PAYMENT_EXCEEDS_REMAINING_DUE", exception.getCode());
        verify(dbBillingWriteModels).markProviderEventStatus("paymob", "777", "failed", 31L, 900L, 10, "Payment amount exceeds invoice due amount");
        verify(dbBillingWriteModels, never()).createBillingPayment(anyInt(), any(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownOrderMarksReservedEventFailed() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(true);
        when(dbBillingWriteModels.lockPaymentAttemptValidationContext("paymob", "123456"))
                .thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.settleTransactionCallback(request));

        assertEquals("PAYMOB_ORDER_NOT_FOUND", exception.getCode());
        verify(dbBillingWriteModels).markProviderEventStatus("paymob", "777", "failed", null, null, null, "Payment attempt not found");
    }

    @Test
    void callbackAttemptValidationFailureMarksReservedEventFailed() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(true);
        BillingPaymentAttemptValidationContext attempt = attempt("CHECKOUT_REQUESTED");
        when(dbBillingWriteModels.lockPaymentAttemptValidationContext("paymob", "123456"))
                .thenReturn(attempt);
        ApiException mismatch = new ApiException(
                org.springframework.http.HttpStatus.CONFLICT,
                "PAYMOB_AMOUNT_MISMATCH",
                "PayMob callback amount does not match the payment attempt"
        );
        Mockito.doThrow(mismatch).when(payMobService).validateCallbackAgainstAttempt(request, attempt);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.settleTransactionCallback(request));

        assertEquals("PAYMOB_AMOUNT_MISMATCH", exception.getCode());
        verify(dbBillingWriteModels).markProviderEventStatus(
                "paymob",
                "777",
                "failed",
                31L,
                900L,
                null,
                "PayMob callback amount does not match the payment attempt"
        );
        verify(dbBillingWriteModels, never()).lockInvoicePaymentContext(900L);
    }

    @Test
    void callbackAttemptCompanyMismatchMarksReservedEventFailedBeforeAllocation() {
        PayMobTransactionCallbackRequest request = request();
        stubVerifiedCallback(request, true);
        when(dbBillingWriteModels.reserveProviderEventProcessing(eq("paymob"), eq("777"), eq("transaction_callback"), eq("123456"), anyString()))
                .thenReturn(true);
        when(dbBillingWriteModels.lockPaymentAttemptValidationContext("paymob", "123456"))
                .thenReturn(new BillingPaymentAttemptValidationContext(
                        31L,
                        900L,
                        99,
                        55,
                        new BigDecimal("300.00"),
                        "EGP",
                        "CHECKOUT_REQUESTED",
                        null
                ));
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L))
                .thenReturn(invoice("300.00", "200.00"));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.settleTransactionCallback(request));

        assertEquals("PAYMOB_ATTEMPT_COMPANY_MISMATCH", exception.getCode());
        verify(dbBillingWriteModels).markProviderEventStatus(
                "paymob",
                "777",
                "failed",
                31L,
                900L,
                10,
                "Payment attempt company does not match invoice company"
        );
        verify(dbBillingWriteModels, never()).createBillingPayment(anyInt(), any(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentAttemptService, never()).markSucceeded(anyString(), anyString(), anyString(), anyString());
    }

    private void stubVerifiedCallback(PayMobTransactionCallbackRequest request, boolean success) {
        when(payMobService.parseVerifiedCallbackWithoutAttemptValidation(request)).thenReturn(new TransactionProcessedCallback(
                777,
                false,
                30000,
                success,
                true,
                false,
                true,
                false,
                false,
                123456
        ));
        when(payMobService.getProviderEventId(request)).thenReturn("777");
        when(payMobService.getExternalOrderId(request)).thenReturn("123456");
    }

    private BillingPaymentAttemptValidationContext attempt(String status) {
        return new BillingPaymentAttemptValidationContext(
                31L,
                900L,
                10,
                55,
                new BigDecimal("300.00"),
                "EGP",
                status,
                null
        );
    }

    private BillingInvoicePaymentContext invoice(String dueAmount, String paidAmount) {
        return new BillingInvoicePaymentContext(
                900L,
                101L,
                1,
                10,
                1001L,
                55,
                "open",
                "EGP",
                new BigDecimal("500.00"),
                new BigDecimal(paidAmount),
                new BigDecimal(dueAmount),
                BigDecimal.ZERO
        );
    }

    private PayMobTransactionCallbackRequest request() {
        return new PayMobTransactionCallbackRequest();
    }
}
