package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingPaymentInitiationCheckoutHydratorTest {

    private BillingProviderCheckoutOutboxProcessor checkoutOutboxProcessor;
    private BillingInvoicePaymentService billingInvoicePaymentService;
    private BillingPaymentInitiationCheckoutHydrator hydrator;

    @BeforeEach
    void setUp() {
        checkoutOutboxProcessor = Mockito.mock(BillingProviderCheckoutOutboxProcessor.class);
        billingInvoicePaymentService = Mockito.mock(BillingInvoicePaymentService.class);
        hydrator = new BillingPaymentInitiationCheckoutHydrator(checkoutOutboxProcessor, billingInvoicePaymentService);
    }

    @Test
    void hydrateCheckoutUrlProcessesOutboxAndCopiesLatestAttemptCheckoutDetails() {
        BillingPaymentInitiationResponse response = response(9001L, new BigDecimal("300.00"), null);
        when(checkoutOutboxProcessor.processDueCheckoutRequests()).thenReturn(1);
        when(billingInvoicePaymentService.latestPaymentAttempt(3001L)).thenReturn(new BillingPaymentAttemptSnapshot(
                9001L,
                "paymob",
                "123456",
                "CHECKOUT_REQUESTED",
                new BigDecimal("300.00"),
                "EGP",
                "https://accept.paymob.com/api/acceptance/iframes/370887?payment_token=token"
        ));

        BillingPaymentInitiationResponse hydrated = hydrator.hydrateCheckoutUrl(response);

        assertEquals("paymob", hydrated.getProviderCode());
        assertEquals("123456", hydrated.getExternalOrderId());
        assertEquals("CHECKOUT_REQUESTED", hydrated.getPaymentAttemptStatus());
        assertEquals("https://accept.paymob.com/api/acceptance/iframes/370887?payment_token=token", hydrated.getCheckoutUrl());
        verify(checkoutOutboxProcessor).processDueCheckoutRequests();
    }

    @Test
    void hydrateCheckoutUrlDoesNotProcessOutboxForBalanceOnlyResponse() {
        BillingPaymentInitiationResponse response = response(null, BigDecimal.ZERO, null);

        BillingPaymentInitiationResponse hydrated = hydrator.hydrateCheckoutUrl(response);

        assertNull(hydrated.getCheckoutUrl());
        verify(checkoutOutboxProcessor, never()).processDueCheckoutRequests();
        verify(billingInvoicePaymentService, never()).latestPaymentAttempt(3001L);
    }

    private BillingPaymentInitiationResponse response(Long attemptId,
                                                      BigDecimal providerAmountDue,
                                                      String checkoutUrl) {
        return new BillingPaymentInitiationResponse(
                3001L,
                10,
                55,
                "CHECKOUT_REQUIRED",
                "open",
                "EGP",
                BigDecimal.ZERO,
                providerAmountDue,
                providerAmountDue,
                BigDecimal.ZERO,
                null,
                null,
                null,
                attemptId,
                null,
                null,
                "CHECKOUT_PENDING",
                checkoutUrl
        );
    }
}
