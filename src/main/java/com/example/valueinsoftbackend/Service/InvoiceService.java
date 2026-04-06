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
        return ensureInvoice(
                billingAccountId,
                branchSubscriptionId,
                "SUB-" + branchSubscriptionId,
                currencyCode,
                amountToPay,
                amountPaid,
                lineDescription,
                "{\"source\":\"modern_branch_subscription\"}",
                "{\"source\":\"modern_branch_subscription\"}"
        );
    }

    public long ensureLegacyMirroredInvoice(long billingAccountId,
                                            long branchSubscriptionId,
                                            int legacySubscriptionId,
                                            String currencyCode,
                                            BigDecimal amountToPay,
                                            BigDecimal amountPaid,
                                            String lineDescription) {
        return ensureInvoice(
                billingAccountId,
                branchSubscriptionId,
                "LEGACY-SUB-" + legacySubscriptionId,
                currencyCode,
                amountToPay,
                amountPaid,
                lineDescription,
                "{\"source\":\"legacy_company_subscription\",\"legacySubscriptionId\":" + legacySubscriptionId + "}",
                "{\"source\":\"legacy_company_subscription\"}"
        );
    }

    private long ensureInvoice(long billingAccountId,
                               long branchSubscriptionId,
                               String invoiceNumber,
                               String currencyCode,
                               BigDecimal amountToPay,
                               BigDecimal amountPaid,
                               String lineDescription,
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
                new Timestamp(System.currentTimeMillis()),
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
                "{\"legacyStatus\":\"PD\"}"
        );
    }

    public void markPaidByBranchSubscriptionId(long branchSubscriptionId) {
        dbBillingWriteModels.updateInvoiceStatusBySource(
                "branch_subscription",
                String.valueOf(branchSubscriptionId),
                "paid",
                BigDecimal.ZERO,
                Instant.now(),
                "{\"legacyStatus\":\"PD\"}"
        );
    }
}
