package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceSettlementSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentPreviewResponse;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingInvoicePaymentServiceTest {

    private DbBillingWriteModels dbBillingWriteModels;
    private BillingEntitlementService billingEntitlementService;
    private PaymentProviderResolver paymentProviderResolver;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private BillingInvoicePaymentService service;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        billingEntitlementService = Mockito.mock(BillingEntitlementService.class);
        paymentProviderResolver = Mockito.mock(PaymentProviderResolver.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        service = new BillingInvoicePaymentService(
                dbBillingWriteModels,
                billingEntitlementService,
                paymentProviderResolver,
                financeOperationalPostingService
        );
    }

    @Test
    void previewUsesAvailableBalanceBeforeProviderAmount() {
        when(dbBillingWriteModels.findInvoicePaymentContext(900L)).thenReturn(context("open", "500.00", "125.00", "200.00"));

        BillingPaymentPreviewResponse response = service.previewPayment(900L);

        assertEquals(new BigDecimal("500.00"), response.getDueAmount());
        assertEquals(new BigDecimal("200.00"), response.getBalanceAppliedAmount());
        assertEquals(new BigDecimal("300.00"), response.getProviderAmountDue());
        assertEquals("BALANCE_THEN_CHECKOUT", response.getPaymentStrategy());
    }

    @Test
    void initiatePaymentPaysInvoiceFullyFromBalance() {
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L)).thenReturn(context("open", "500.00", "0.00", "700.00"));
        stubBalanceInsertIds();

        BillingPaymentInitiationResponse response = service.initiatePayment(
                900L,
                new BillingPaymentInitiationRequest("key-900", true)
        );

        assertEquals("PAID_FROM_BALANCE", response.getStatus());
        assertEquals(new BigDecimal("500.00"), response.getBalanceAppliedAmount());
        assertEquals(new BigDecimal("0.00"), response.getRemainingDueAmount());
        assertEquals(new BigDecimal("200.00"), response.getAvailableBalance());
        assertEquals(21L, response.getBillingPaymentId());
        assertEquals(22L, response.getBillingPaymentAllocationId());
        assertEquals(23L, response.getBillingAccountLedgerId());

        verify(dbBillingWriteModels).updateBillingAccountAvailableBalance(101L, new BigDecimal("200.00"));
        verify(financeOperationalPostingService).enqueueBillingBalanceSettlement(
                eq(10),
                eq(55),
                eq(900L),
                eq(21L),
                eq(22L),
                eq(new BigDecimal("500.00")),
                eq("EGP"),
                any(),
                eq("system")
        );
        verify(dbBillingWriteModels).updateInvoicePaymentProjection(
                eq(900L),
                eq("paid"),
                eq(new BigDecimal("500.00")),
                eq(new BigDecimal("0.00")),
                any(Instant.class),
                anyString()
        );
        verify(paymentProviderResolver, never()).getActiveProvider();
    }

    @Test
    void initiatePaymentAppliesPartialBalanceThenCreatesProviderCheckout() {
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L)).thenReturn(context("open", "500.00", "0.00", "200.00"));
        stubBalanceInsertIds();

        PaymentProvider paymentProvider = Mockito.mock(PaymentProvider.class);
        when(paymentProviderResolver.getActiveProvider()).thenReturn(paymentProvider);
        when(paymentProvider.getProviderCode()).thenReturn("mock");
        when(paymentProvider.createProviderOrder(anyInt(), eq(55), eq(new BigDecimal("300.00")))).thenReturn(123456);
        when(paymentProvider.createPaymentKeyUrl(any(PaymentTokenRequest.class))).thenReturn("https://checkout.example/pay");
        when(dbBillingWriteModels.createPaymentAttempt(
                anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(31L);

        BillingPaymentInitiationResponse response = service.initiatePayment(
                900L,
                new BillingPaymentInitiationRequest("key-900", true)
        );

        assertEquals("PARTIAL_BALANCE_CHECKOUT_REQUIRED", response.getStatus());
        assertEquals(new BigDecimal("200.00"), response.getBalanceAppliedAmount());
        assertEquals(new BigDecimal("300.00"), response.getProviderAmountDue());
        assertEquals(31L, response.getBillingPaymentAttemptId());
        assertEquals("mock", response.getProviderCode());
        assertEquals("123456", response.getExternalOrderId());

        verify(dbBillingWriteModels).updateInvoicePaymentProjection(
                eq(900L),
                eq("open"),
                eq(new BigDecimal("200.00")),
                eq(new BigDecimal("300.00")),
                isNull(),
                anyString()
        );
        verify(dbBillingWriteModels).supersedeActivePaymentAttempts(900L, "mock");
    }

    @Test
    void initiatePaymentWithDuplicateIdempotencyKeyReturnsExistingSettlementAndAttempt() {
        when(dbBillingWriteModels.lockInvoicePaymentContext(900L)).thenReturn(context("open", "300.00", "200.00", "0.00"));
        when(dbBillingWriteModels.findBalanceSettlementByIdempotencyKey(10, 900L, "key-900"))
                .thenReturn(new BillingBalanceSettlementSnapshot(
                        21L,
                        22L,
                        23L,
                        new BigDecimal("200.00"),
                        "EGP"
                ));
        when(dbBillingWriteModels.findPaymentAttemptByCompanyIdempotency(10, "key-900"))
                .thenReturn(new BillingPaymentAttemptSnapshot(
                        31L,
                        "mock",
                        "123456",
                        "CHECKOUT_REQUESTED",
                        new BigDecimal("300.00"),
                        "EGP",
                        "https://checkout.example/pay"
                ));

        BillingPaymentInitiationResponse response = service.initiatePayment(
                900L,
                new BillingPaymentInitiationRequest("key-900", true)
        );

        assertEquals("PARTIAL_BALANCE_CHECKOUT_REQUIRED", response.getStatus());
        assertEquals(new BigDecimal("200.00"), response.getBalanceAppliedAmount());
        assertEquals(new BigDecimal("300.00"), response.getProviderAmountDue());
        assertEquals(21L, response.getBillingPaymentId());
        assertEquals(31L, response.getBillingPaymentAttemptId());
        assertEquals("https://checkout.example/pay", response.getCheckoutUrl());

        verify(dbBillingWriteModels, never()).createBillingPayment(
                anyInt(), any(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
        verify(paymentProviderResolver, never()).getActiveProvider();
    }

    private void stubBalanceInsertIds() {
        when(dbBillingWriteModels.createBillingAccountLedgerEntry(
                anyLong(), anyInt(), anyString(), any(), anyString(), anyString(), any(), any(),
                anyString(), anyString(), anyString(), any(), any(), any(), anyString(), anyString()
        )).thenReturn(23L);
        when(dbBillingWriteModels.createBillingPayment(
                anyInt(), anyLong(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(21L);
        when(dbBillingWriteModels.createBillingPaymentAllocation(anyLong(), anyLong(), any(), anyString())).thenReturn(22L);
    }

    private BillingInvoicePaymentContext context(String status,
                                                 String dueAmount,
                                                 String paidAmount,
                                                 String availableBalance) {
        return new BillingInvoicePaymentContext(
                900L,
                101L,
                10,
                10,
                1001L,
                55,
                status,
                "EGP",
                new BigDecimal("500.00"),
                new BigDecimal(paidAmount),
                new BigDecimal(dueAmount),
                new BigDecimal(availableBalance)
        );
    }
}
