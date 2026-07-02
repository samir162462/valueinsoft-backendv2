package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.Model.Billing.BillingProviderCheckoutOutboxItem;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingProviderCheckoutOutboxProcessorTest {

    private DbBillingWriteModels dbBillingWriteModels;
    private PaymentProviderResolver paymentProviderResolver;
    private BillingProperties billingProperties;
    private PaymentProvider paymentProvider;
    private BillingProviderCheckoutOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        paymentProviderResolver = Mockito.mock(PaymentProviderResolver.class);
        billingProperties = new BillingProperties();
        paymentProvider = Mockito.mock(PaymentProvider.class);
        processor = new BillingProviderCheckoutOutboxProcessor(
                dbBillingWriteModels,
                paymentProviderResolver,
                billingProperties,
                new ObjectMapper()
        );
    }

    @Test
    void processDueCheckoutCreatesProviderCheckoutAndMarksAttemptRequested() {
        when(dbBillingWriteModels.claimDueProviderCheckoutOutboxItems(10)).thenReturn(List.of(outboxItem(1)));
        when(paymentProviderResolver.getProvider("mock")).thenReturn(paymentProvider);
        when(paymentProvider.createProviderOrder(eq(100000031), eq(55), eq(new BigDecimal("300.00")))).thenReturn(123456);
        when(paymentProvider.createPaymentKeyUrl(any(PaymentTokenRequest.class))).thenReturn("https://checkout.example/pay");
        when(dbBillingWriteModels.updatePaymentAttemptCheckoutRequestedById(
                eq(31L), eq("123456"), eq("checkout-ref-31"), anyString(), anyString()
        )).thenReturn(1);

        int processed = processor.processDueCheckoutRequests();

        assertEquals(1, processed);
        ArgumentCaptor<PaymentTokenRequest> tokenRequestCaptor = ArgumentCaptor.forClass(PaymentTokenRequest.class);
        verify(paymentProvider).createPaymentKeyUrl(tokenRequestCaptor.capture());
        assertEquals(123456L, tokenRequestCaptor.getValue().getOrderId());
        assertEquals(55, tokenRequestCaptor.getValue().getBranchId());
        assertEquals(10, tokenRequestCaptor.getValue().getCompanyId());
        assertEquals("EGP", tokenRequestCaptor.getValue().getCurrency());
        assertEquals(30000L, tokenRequestCaptor.getValue().getAmountCents());
        verify(dbBillingWriteModels).updatePaymentAttemptCheckoutRequestedById(
                eq(31L), eq("123456"), eq("checkout-ref-31"), anyString(), anyString()
        );
        verify(dbBillingWriteModels).markProviderCheckoutOutboxSucceeded(eq(1L), anyString());
    }

    @Test
    void processDueCheckoutMarksUncertainWhenProviderOrderExistsButCheckoutUrlFails() {
        when(dbBillingWriteModels.claimDueProviderCheckoutOutboxItems(10)).thenReturn(List.of(outboxItem(1)));
        when(paymentProviderResolver.getProvider("mock")).thenReturn(paymentProvider);
        when(paymentProvider.createProviderOrder(anyInt(), eq(55), eq(new BigDecimal("300.00")))).thenReturn(123456);
        when(paymentProvider.createPaymentKeyUrl(any(PaymentTokenRequest.class))).thenThrow(new IllegalStateException("token failed"));

        int processed = processor.processDueCheckoutRequests();

        assertEquals(1, processed);
        verify(dbBillingWriteModels).updatePaymentAttemptPendingProviderById(
                eq(31L),
                eq("123456"),
                anyString(),
                eq("PROVIDER_CHECKOUT_UNCERTAIN"),
                eq("token failed")
        );
        verify(dbBillingWriteModels).markProviderCheckoutOutboxFinal(
                eq(1L),
                eq("FAILED_UNCERTAIN"),
                anyString(),
                eq("token failed")
        );
    }

    private BillingProviderCheckoutOutboxItem outboxItem(long checkoutOutboxId) {
        return new BillingProviderCheckoutOutboxItem(
                checkoutOutboxId,
                31L,
                900L,
                10,
                55,
                "mock",
                "CREATE_CHECKOUT",
                "key-900",
                new BigDecimal("300.00"),
                "EGP",
                "checkout-ref-31",
                "{}",
                1
        );
    }
}
