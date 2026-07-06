package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

@Service
public class InvoiceService {

    private final DbBillingWriteModels dbBillingWriteModels;

    public InvoiceService(DbBillingWriteModels dbBillingWriteModels) {
        this.dbBillingWriteModels = dbBillingWriteModels;
    }

    public long ensureBranchSubscriptionInvoice(long billingAccountId,
                                                long branchSubscriptionId,
                                                String currencyCode,
                                                BigDecimal amountToPay,
                                                BigDecimal amountPaid,
                                                String lineDescription) {
        return ensureBranchSubscriptionInvoice(
                billingAccountId,
                branchSubscriptionId,
                currencyCode,
                amountToPay,
                amountPaid,
                lineDescription,
                new Timestamp(System.currentTimeMillis())
        );
    }

    public long ensureBranchSubscriptionInvoice(long billingAccountId,
                                                long branchSubscriptionId,
                                                String currencyCode,
                                                BigDecimal amountToPay,
                                                BigDecimal amountPaid,
                                                String lineDescription,
                                                Timestamp dueAt) {
        return ensureInvoice(
                billingAccountId,
                branchSubscriptionId,
                "SUB-" + branchSubscriptionId,
                currencyCode,
                amountToPay,
                amountPaid,
                lineDescription,
                dueAt,
                "{\"source\":\"modern_branch_subscription\"}",
                "{\"source\":\"modern_branch_subscription\"}"
        );
    }

    private long ensureInvoice(long billingAccountId,
                               long branchSubscriptionId,
                               String invoiceNumber,
                               String currencyCode,
                               BigDecimal amountToPay,
                               BigDecimal amountPaid,
                               String lineDescription,
                               Timestamp dueAt,
                               String metadataJson,
                               String lineMetadataJson) {
        Long existingId = dbBillingWriteModels.findInvoiceIdBySource("branch_subscription", String.valueOf(branchSubscriptionId));
        if (existingId != null) {
            return existingId;
        }

        BigDecimal paidAmount = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        BigDecimal dueAmount = amountToPay.subtract(paidAmount).max(BigDecimal.ZERO);
        String invoiceStatus = dueAmount.compareTo(BigDecimal.ZERO) == 0 ? "paid" : "open";
        long invoiceId = dbBillingWriteModels.createInvoice(
                billingAccountId,
                invoiceNumber,
                invoiceStatus,
                currencyCode,
                amountToPay,
                amountToPay,
                dueAmount,
                dueAt == null ? new Timestamp(System.currentTimeMillis()) : dueAt,
                "branch_subscription",
                String.valueOf(branchSubscriptionId),
                metadataJson
        );

        if (!dbBillingWriteModels.invoiceLineExists(invoiceId, branchSubscriptionId)) {
            dbBillingWriteModels.createInvoiceLine(
                    invoiceId,
                    branchSubscriptionId,
                    lineDescription,
                    1,
                    amountToPay,
                    amountToPay,
                    lineMetadataJson
            );
        }

        return invoiceId;
    }

    public void markPaidByExternalOrderId(String providerCode, String externalOrderId) {
        dbBillingWriteModels.updateInvoiceStatusByExternalOrderId(
                providerCode,
                externalOrderId,
                "paid",
                BigDecimal.ZERO,
                Instant.now(),
                "{\"source\":\"provider_payment\",\"status\":\"paid\"}"
        );
    }
}
