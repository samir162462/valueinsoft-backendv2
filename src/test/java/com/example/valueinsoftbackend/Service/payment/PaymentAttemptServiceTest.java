package com.example.valueinsoftbackend.Service.payment;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAttemptServiceTest {

    private DbBillingWriteModels dbBillingWriteModels;
    private PaymentAttemptService service;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        service = new PaymentAttemptService(dbBillingWriteModels);
    }

    @Test
    void ensureCreatedAttemptUsesCentralCreatedStatus() {
        when(dbBillingWriteModels.findPaymentAttemptId("paymob", "123456")).thenReturn(null);
        when(dbBillingWriteModels.createPaymentAttempt(
                eq(3001L),
                eq("paymob"),
                eq("123456"),
                eq("created"),
                eq(new BigDecimal("400.00")),
                eq("EGP"),
                anyString(),
                anyString()
        )).thenReturn(9001L);

        long attemptId = service.ensureCreatedAttempt(
                3001L,
                "paymob",
                "123456",
                new BigDecimal("400.00"),
                "EGP",
                "{}",
                "{}"
        );

        assertEquals(9001L, attemptId);
    }

    @Test
    void markCheckoutRequestedThrowsWhenAttemptIsTerminalOrMissing() {
        when(dbBillingWriteModels.updatePaymentAttemptCheckoutRequest("paymob", "123456", "checkout_requested", "{}"))
                .thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.markCheckoutRequested("paymob", "123456", "{}"));

        assertEquals("BILLING_PAYMENT_ATTEMPT_TERMINAL_OR_NOT_FOUND", exception.getCode());
    }

    @Test
    void markSucceededUsesTerminalGuardedUpdate() {
        when(dbBillingWriteModels.completePaymentAttempt(
                eq("paymob"),
                eq("123456"),
                eq("succeeded"),
                eq("{}"),
                eq("txn-1"),
                any(),
                any()
        )).thenReturn(1);

        service.markSucceeded("paymob", "123456", "{}", "txn-1");

        verify(dbBillingWriteModels).completePaymentAttempt(
                "paymob",
                "123456",
                "succeeded",
                "{}",
                "txn-1",
                null,
                null
        );
    }
}
