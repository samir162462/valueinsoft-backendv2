package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountBalanceResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoiceMutationContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAllocationReversalCandidate;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingManualActionResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.ManualBillingActionRequest;
import com.example.valueinsoftbackend.Service.billing.BillingEntitlementService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualBillingAdjustmentServiceTest {

    private DbBillingWriteModels dbBillingWriteModels;
    private BillingEntitlementService billingEntitlementService;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private ManualBillingAdjustmentService service;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        billingEntitlementService = Mockito.mock(BillingEntitlementService.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        service = new ManualBillingAdjustmentService(
                dbBillingWriteModels,
                billingEntitlementService,
                financeOperationalPostingService
        );
    }

    @Test
    void refundInvoiceCreatesReversalPaymentRestoresCompanyBalanceAndQueuesFinancePosting() {
        ManualBillingActionRequest request = new ManualBillingActionRequest();
        request.setReference("refund-ref-900");
        request.setNote("customer cancellation");

        when(dbBillingWriteModels.findInvoiceMutationContext(900L)).thenReturn(context());
        when(dbBillingWriteModels.createCompletedPaymentAttempt(
                eq(900L), eq("manual_refund"), eq("refund-ref-900"), eq("refund-ref-900"),
                eq("succeeded"), eq(new BigDecimal("500.00")), eq("EGP"), anyString(), anyString()
        )).thenReturn(31L);
        when(dbBillingWriteModels.findInvoicePaymentAllocationsForReversal(900L)).thenReturn(List.of(
                new BillingPaymentAllocationReversalCandidate(
                        21L,
                        22L,
                        900L,
                        101L,
                        "COMPANY_BALANCE",
                        null,
                        "BALANCE-900",
                        new BigDecimal("500.00"),
                        new BigDecimal("500.00"),
                        "EGP"
                )
        ));
        when(dbBillingWriteModels.createBillingPayment(
                eq(10), eq(101L), eq("COMPANY_BALANCE_REVERSAL"), any(), eq(new BigDecimal("500.00")),
                eq("EGP"), eq("REVERSED"), eq("refund-ref-900"), anyString(), anyString()
        )).thenReturn(41L);
        when(dbBillingWriteModels.createBillingPaymentAllocation(41L, 900L, new BigDecimal("500.00"), "EGP"))
                .thenReturn(42L);
        when(dbBillingWriteModels.lockBillingAccountBalance(10, "EGP")).thenReturn(new BillingAccountBalanceResponse(
                10,
                101L,
                "EGP",
                new BigDecimal("50.00"),
                "active",
                4L,
                new Timestamp(System.currentTimeMillis())
        ));

        PlatformBillingManualActionResponse response = service.refundInvoice(900L, request, "sam");

        assertEquals("manual_refund", response.getActionType());
        assertEquals("refunded", response.getInvoiceStatus());
        assertEquals(new BigDecimal("500.00"), response.getAmount());
        assertEquals(new BigDecimal("500.00"), response.getDueAmount());

        verify(dbBillingWriteModels).createBillingPayment(
                eq(10), eq(101L), eq("COMPANY_BALANCE_REVERSAL"), any(), eq(new BigDecimal("500.00")),
                eq("EGP"), eq("REVERSED"), eq("refund-ref-900"), anyString(), anyString()
        );
        verify(dbBillingWriteModels).updateBillingAccountAvailableBalance(101L, new BigDecimal("550.00"));
        verify(dbBillingWriteModels).createBillingAccountLedgerEntry(
                eq(101L),
                eq(10),
                eq("REFUND_REVERSAL"),
                eq(new BigDecimal("500.00")),
                eq("EGP"),
                eq("CREDIT"),
                eq(new BigDecimal("50.00")),
                eq(new BigDecimal("550.00")),
                eq("billing_payment"),
                eq("41"),
                anyString(),
                eq("REVERSAL"),
                eq("REFUND_CREDIT"),
                eq("APPROVED"),
                anyString(),
                anyString()
        );
        verify(financeOperationalPostingService).enqueueBillingPaymentReversal(
                eq(10),
                eq(55),
                eq(900L),
                eq(41L),
                eq(42L),
                eq(21L),
                eq(22L),
                eq(new BigDecimal("500.00")),
                eq("EGP"),
                eq("COMPANY_BALANCE_REVERSAL"),
                eq("COMPANY_BALANCE"),
                any(),
                eq("refund-ref-900"),
                any(),
                eq("sam")
        );
        verify(dbBillingWriteModels).updateInvoiceManualState(
                eq(900L),
                eq("refunded"),
                eq(new BigDecimal("0.00")),
                eq(new BigDecimal("500.00")),
                any(),
                anyString()
        );
    }

    private BillingInvoiceMutationContext context() {
        return new BillingInvoiceMutationContext(
                900L,
                101L,
                1001L,
                1,
                10,
                55,
                "paid",
                new BigDecimal("500.00"),
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                "EGP"
        );
    }
}
