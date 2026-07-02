package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BillingPaymentInitiationCheckoutHydrator {

    private final BillingProviderCheckoutOutboxProcessor checkoutOutboxProcessor;
    private final BillingInvoicePaymentService billingInvoicePaymentService;

    public BillingPaymentInitiationCheckoutHydrator(BillingProviderCheckoutOutboxProcessor checkoutOutboxProcessor,
                                                    BillingInvoicePaymentService billingInvoicePaymentService) {
        this.checkoutOutboxProcessor = checkoutOutboxProcessor;
        this.billingInvoicePaymentService = billingInvoicePaymentService;
    }

    public BillingPaymentInitiationResponse hydrateCheckoutUrl(BillingPaymentInitiationResponse response) {
        if (!shouldHydrate(response)) {
            return response;
        }

        checkoutOutboxProcessor.processDueCheckoutRequests();

        BillingPaymentAttemptSnapshot latestAttempt =
                billingInvoicePaymentService.latestPaymentAttempt(response.getBillingInvoiceId());
        if (latestAttempt == null || !sameAttempt(response, latestAttempt)) {
            return response;
        }

        response.setProviderCode(latestAttempt.getProviderCode());
        response.setExternalOrderId(latestAttempt.getExternalOrderId());
        response.setPaymentAttemptStatus(latestAttempt.getStatus());
        response.setCheckoutUrl(latestAttempt.getCheckoutUrl());
        return response;
    }

    private boolean shouldHydrate(BillingPaymentInitiationResponse response) {
        if (response == null || response.getBillingPaymentAttemptId() == null) {
            return false;
        }
        if (response.getCheckoutUrl() != null && !response.getCheckoutUrl().isBlank()) {
            return false;
        }
        BigDecimal providerAmountDue = response.getProviderAmountDue();
        return providerAmountDue != null && providerAmountDue.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean sameAttempt(BillingPaymentInitiationResponse response, BillingPaymentAttemptSnapshot latestAttempt) {
        return response.getBillingPaymentAttemptId() != null
                && response.getBillingPaymentAttemptId() == latestAttempt.getBillingPaymentAttemptId();
    }
}
