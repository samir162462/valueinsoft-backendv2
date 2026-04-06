package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import org.springframework.stereotype.Service;

@Service
public class BillingEntitlementService {

    private final DbBillingWriteModels dbBillingWriteModels;

    public BillingEntitlementService(DbBillingWriteModels dbBillingWriteModels) {
        this.dbBillingWriteModels = dbBillingWriteModels;
    }

    public void recordPendingPayment(int branchId, long branchSubscriptionId, long billingInvoiceId, int legacySubscriptionId) {
        dbBillingWriteModels.createEntitlementEvent(
                branchId,
                branchSubscriptionId,
                billingInvoiceId,
                "subscription_pending_payment",
                null,
                "pending_payment",
                "legacy_subscription_created",
                "{\"legacySubscriptionId\":" + legacySubscriptionId + "}"
        );
    }

    public void recordActivated(int branchId, String externalOrderId) {
        dbBillingWriteModels.createEntitlementEvent(
                branchId,
                null,
                null,
                "subscription_paid",
                "pending_payment",
                "active",
                "provider_payment_success",
                "{\"externalOrderId\":\"" + externalOrderId + "\"}"
        );
    }

    public void recordPendingRenewal(int branchId,
                                     long branchSubscriptionId,
                                     long billingInvoiceId,
                                     long previousBranchSubscriptionId) {
        dbBillingWriteModels.createEntitlementEvent(
                branchId,
                branchSubscriptionId,
                billingInvoiceId,
                "subscription_renewal_pending_payment",
                "active",
                "pending_payment",
                "renewal_invoice_generated",
                "{\"previousBranchSubscriptionId\":" + previousBranchSubscriptionId + "}"
        );
    }

    public void recordPastDue(int branchId,
                              long branchSubscriptionId,
                              long billingInvoiceId,
                              int attemptNumber) {
        dbBillingWriteModels.createEntitlementEvent(
                branchId,
                branchSubscriptionId,
                billingInvoiceId,
                "subscription_past_due",
                "pending_payment",
                "past_due",
                "dunning_attempt_" + attemptNumber,
                "{\"attemptNumber\":" + attemptNumber + "}"
        );
    }
}
