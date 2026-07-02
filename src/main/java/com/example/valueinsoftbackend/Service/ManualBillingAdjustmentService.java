package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountBalanceResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoiceMutationContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAllocationReversalCandidate;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingManualActionResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.ManualBillingActionRequest;
import com.example.valueinsoftbackend.Service.billing.BillingEntitlementService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ManualBillingAdjustmentService {

    private final DbBillingWriteModels dbBillingWriteModels;
    private final BillingEntitlementService billingEntitlementService;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public ManualBillingAdjustmentService(DbBillingWriteModels dbBillingWriteModels,
                                          BillingEntitlementService billingEntitlementService,
                                          FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.billingEntitlementService = billingEntitlementService;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    @Transactional
    public PlatformBillingManualActionResponse recordManualPayment(long billingInvoiceId,
                                                                   ManualBillingActionRequest request,
                                                                   String actorUserName) {
        BillingInvoiceMutationContext context = requireInvoice(billingInvoiceId);
        if (context.getDueAmount() == null || context.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_INVOICE_NOT_PAYABLE", "Invoice has no due amount to pay");
        }

        BigDecimal amount = request.getAmount() == null ? context.getDueAmount() : request.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(context.getDueAmount()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MANUAL_PAYMENT_AMOUNT_INVALID", "Manual payment amount must be positive and not exceed the due amount");
        }

        String providerCode = "manual_" + normalizePaymentMethod(request.getPaymentMethod());
        String reference = firstNonBlank(request.getReference(), providerCode + "-" + billingInvoiceId + "-" + System.currentTimeMillis());
        String externalOrderId = "manual-" + billingInvoiceId + "-" + System.currentTimeMillis();
        BigDecimal newDueAmount = context.getDueAmount().subtract(amount).max(BigDecimal.ZERO);
        boolean fullyPaid = newDueAmount.compareTo(BigDecimal.ZERO) == 0;
        long attemptId = dbBillingWriteModels.createCompletedPaymentAttempt(
                billingInvoiceId,
                providerCode,
                externalOrderId,
                reference,
                "succeeded",
                amount,
                normalizeCurrency(context.getCurrencyCode()),
                json("manual_payment", actorUserName, request.getNote()),
                json("manual_payment", actorUserName, request.getNote())
        );

        dbBillingWriteModels.updateInvoiceManualState(
                billingInvoiceId,
                fullyPaid ? "paid" : "open",
                totalAmountOrZero(context).subtract(newDueAmount).max(BigDecimal.ZERO),
                newDueAmount,
                fullyPaid ? Instant.now() : null,
                json("manual_payment", actorUserName, request.getNote())
        );

        if (fullyPaid && context.getBranchSubscriptionId() != null) {
            dbBillingWriteModels.updateBranchSubscriptionStatusById(
                    context.getBranchSubscriptionId(),
                    "active",
                    json("manual_payment", actorUserName, request.getNote())
            );
            recordEntitlement(context, "pending_payment", "active", "manual_payment", request.getNote());
        }

        return response(context, "manual_payment", fullyPaid ? "paid" : "open", fullyPaid ? "active" : "pending_payment",
                amount, newDueAmount, providerCode, reference, attemptId);
    }

    @Transactional
    public PlatformBillingManualActionResponse markInvoiceUnpaid(long billingInvoiceId,
                                                                 ManualBillingActionRequest request,
                                                                 String actorUserName) {
        BillingInvoiceMutationContext context = requireInvoice(billingInvoiceId);
        if (!"paid".equalsIgnoreCase(context.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_INVOICE_NOT_PAID", "Only paid invoices can be marked unpaid");
        }

        BigDecimal dueAmount = totalAmountOrZero(context);
        String reference = firstNonBlank(request.getReference(), "manual-unpaid-" + billingInvoiceId + "-" + System.currentTimeMillis());
        long attemptId = dbBillingWriteModels.createCompletedPaymentAttempt(
                billingInvoiceId,
                "manual_unpaid",
                reference,
                reference,
                "reversed",
                dueAmount,
                normalizeCurrency(context.getCurrencyCode()),
                json("manual_unpaid", actorUserName, request.getNote()),
                json("manual_unpaid", actorUserName, request.getNote())
        );
        dbBillingWriteModels.updateInvoiceManualState(
                billingInvoiceId,
                "open",
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                dueAmount,
                null,
                json("manual_unpaid", actorUserName, request.getNote())
        );
        if (context.getBranchSubscriptionId() != null) {
            dbBillingWriteModels.updateBranchSubscriptionStatusById(
                    context.getBranchSubscriptionId(),
                    "pending_payment",
                    json("manual_unpaid", actorUserName, request.getNote())
            );
            recordEntitlement(context, "active", "pending_payment", "manual_unpaid", request.getNote());
        }

        return response(context, "manual_unpaid", "open", "pending_payment", dueAmount, dueAmount,
                "manual_unpaid", reference, attemptId);
    }

    @Transactional
    public PlatformBillingManualActionResponse refundInvoice(long billingInvoiceId,
                                                            ManualBillingActionRequest request,
                                                            String actorUserName) {
        BillingInvoiceMutationContext context = requireInvoice(billingInvoiceId);
        if (!"paid".equalsIgnoreCase(context.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_INVOICE_NOT_PAID", "Only paid invoices can be refunded");
        }

        BigDecimal invoiceTotal = totalAmountOrZero(context);
        BigDecimal amount = request.getAmount() == null ? invoiceTotal : request.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(invoiceTotal) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MANUAL_REFUND_AMOUNT_INVALID", "Refund amount must be positive and not exceed invoice total");
        }

        String reference = firstNonBlank(request.getReference(), "manual-refund-" + billingInvoiceId + "-" + System.currentTimeMillis());
        long attemptId = dbBillingWriteModels.createCompletedPaymentAttempt(
                billingInvoiceId,
                "manual_refund",
                reference,
                reference,
                "succeeded",
                amount,
                normalizeCurrency(context.getCurrencyCode()),
                json("manual_refund", actorUserName, request.getNote()),
                json("manual_refund", actorUserName, request.getNote())
        );
        createRefundReversalRecords(context, amount, reference, actorUserName, request.getNote());

        BigDecimal remainingPaidAmount = amountOrZero(context.getPaidAmount()).subtract(amount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextDueAmount = invoiceTotal.subtract(remainingPaidAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean fullyRefunded = remainingPaidAmount.compareTo(BigDecimal.ZERO) == 0;
        dbBillingWriteModels.updateInvoiceManualState(
                billingInvoiceId,
                fullyRefunded ? "refunded" : "open",
                remainingPaidAmount,
                nextDueAmount,
                null,
                json("manual_refund", actorUserName, request.getNote())
        );
        if (context.getBranchSubscriptionId() != null) {
            dbBillingWriteModels.updateBranchSubscriptionStatusById(
                    context.getBranchSubscriptionId(),
                    "refunded",
                    json("manual_refund", actorUserName, request.getNote())
            );
            recordEntitlement(context, "active", "refunded", "manual_refund", request.getNote());
        }

        return response(context, "manual_refund", fullyRefunded ? "refunded" : "open", fullyRefunded ? "refunded" : "pending_payment", amount, nextDueAmount,
                "manual_refund", reference, attemptId);
    }

    private void createRefundReversalRecords(BillingInvoiceMutationContext context,
                                             BigDecimal refundAmount,
                                             String reference,
                                             String actorUserName,
                                             String note) {
        BigDecimal remaining = refundAmount.setScale(2, RoundingMode.HALF_UP);
        List<BillingPaymentAllocationReversalCandidate> candidates =
                dbBillingWriteModels.findInvoicePaymentAllocationsForReversal(context.getBillingInvoiceId());
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_REFUND_NO_ALLOCATIONS", "No allocated billing payment records are available to reverse");
        }

        for (BillingPaymentAllocationReversalCandidate candidate : candidates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal reversible = amountOrZero(candidate.getReversibleAmount());
            if (reversible.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal reversalAmount = remaining.min(reversible).setScale(2, RoundingMode.HALF_UP);
            String idempotencyKey = "manual-refund:" + context.getCompanyId() + ":" + context.getBillingInvoiceId() + ":" +
                    candidate.getBillingPaymentAllocationId() + ":" + Integer.toUnsignedString(reference.hashCode());
            String reversalSource = reversalPaymentSource(candidate.getPaymentSource());
            String metadataJson = refundMetadata(reference, actorUserName, note, candidate);

            long reversalPaymentId = dbBillingWriteModels.createBillingPayment(
                    context.getCompanyId(),
                    context.getBillingAccountId(),
                    reversalSource,
                    candidate.getProviderCode(),
                    reversalAmount,
                    normalizeCurrency(candidate.getCurrencyCode()),
                    "REVERSED",
                    reference,
                    idempotencyKey,
                    metadataJson
            );
            long reversalAllocationId = dbBillingWriteModels.createBillingPaymentAllocation(
                    reversalPaymentId,
                    context.getBillingInvoiceId(),
                    reversalAmount,
                    normalizeCurrency(candidate.getCurrencyCode())
            );

            if ("COMPANY_BALANCE".equalsIgnoreCase(candidate.getPaymentSource())) {
                restoreCompanyBalance(context, reversalAmount, reference, idempotencyKey, actorUserName, note, reversalPaymentId);
            }

            financeOperationalPostingService.enqueueBillingPaymentReversal(
                    context.getCompanyId(),
                    context.getBranchId(),
                    context.getBillingInvoiceId(),
                    reversalPaymentId,
                    reversalAllocationId,
                    candidate.getBillingPaymentId(),
                    candidate.getBillingPaymentAllocationId(),
                    reversalAmount,
                    normalizeCurrency(candidate.getCurrencyCode()),
                    reversalSource,
                    candidate.getPaymentSource(),
                    candidate.getProviderCode(),
                    reference,
                    new Timestamp(System.currentTimeMillis()),
                    actorUserName
            );
            remaining = remaining.subtract(reversalAmount).setScale(2, RoundingMode.HALF_UP);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_REFUND_EXCEEDS_ALLOCATIONS", "Refund amount exceeds reversible billing payment allocations");
        }
    }

    private void restoreCompanyBalance(BillingInvoiceMutationContext context,
                                       BigDecimal reversalAmount,
                                       String reference,
                                       String idempotencyKey,
                                       String actorUserName,
                                       String note,
                                       long reversalPaymentId) {
        BillingAccountBalanceResponse account =
                dbBillingWriteModels.lockBillingAccountBalance(context.getCompanyId(), normalizeCurrency(context.getCurrencyCode()));
        if (account == null) {
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_ACCOUNT_NOT_FOUND", "Billing account was not found for refund balance reversal");
        }

        BigDecimal balanceBefore = amountOrZero(account.getAvailableBalance());
        BigDecimal balanceAfter = balanceBefore.add(reversalAmount).setScale(2, RoundingMode.HALF_UP);
        dbBillingWriteModels.updateBillingAccountAvailableBalance(account.getBillingAccountId(), balanceAfter);
        dbBillingWriteModels.createBillingAccountLedgerEntry(
                account.getBillingAccountId(),
                context.getCompanyId(),
                "REFUND_REVERSAL",
                reversalAmount,
                normalizeCurrency(context.getCurrencyCode()),
                "CREDIT",
                balanceBefore,
                balanceAfter,
                "billing_payment",
                String.valueOf(reversalPaymentId),
                "balance-reversal:" + context.getCompanyId() + ":" + context.getBillingInvoiceId() + ":" +
                        reversalPaymentId + ":" + Integer.toUnsignedString(idempotencyKey.hashCode()),
                "REVERSAL",
                "REFUND_CREDIT",
                "APPROVED",
                "Refund restored company balance for invoice " + context.getBillingInvoiceId(),
                "{\"source\":\"manual_refund\",\"reference\":\"" + escape(reference) + "\",\"actorUserName\":\"" + escape(actorUserName) +
                        "\",\"note\":\"" + escape(note) + "\"}"
        );
    }

    private BillingInvoiceMutationContext requireInvoice(long billingInvoiceId) {
        BillingInvoiceMutationContext context = dbBillingWriteModels.findInvoiceMutationContext(billingInvoiceId);
        if (context == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BILLING_INVOICE_NOT_FOUND", "Billing invoice not found");
        }
        if (context.getBranchId() == null || context.getBranchSubscriptionId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_INVOICE_NOT_BRANCH_SUBSCRIPTION", "Manual branch subscription actions require a branch subscription invoice");
        }
        return context;
    }

    private void recordEntitlement(BillingInvoiceMutationContext context,
                                   String fromState,
                                   String toState,
                                   String reasonCode,
                                   String note) {
        billingEntitlementService.recordManualStateChange(
                context.getBranchId(),
                context.getBranchSubscriptionId(),
                context.getBillingInvoiceId(),
                fromState,
                toState,
                reasonCode,
                json(reasonCode, null, note)
        );
    }

    private PlatformBillingManualActionResponse response(BillingInvoiceMutationContext context,
                                                         String actionType,
                                                         String invoiceStatus,
                                                         String branchSubscriptionStatus,
                                                         BigDecimal amount,
                                                         BigDecimal dueAmount,
                                                         String providerCode,
                                                         String reference,
                                                         Long paymentAttemptId) {
        return new PlatformBillingManualActionResponse(
                context.getBillingInvoiceId(),
                paymentAttemptId,
                context.getBranchSubscriptionId(),
                context.getBranchId(),
                context.getTenantId(),
                actionType,
                invoiceStatus,
                branchSubscriptionStatus,
                amount,
                dueAmount,
                normalizeCurrency(context.getCurrencyCode()),
                providerCode,
                reference,
                new Timestamp(System.currentTimeMillis())
        );
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "cash";
        }
        String value = paymentMethod.trim().toLowerCase(Locale.ROOT);
        return "instapay".equals(value) ? "instapay" : "cash";
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private BigDecimal totalAmountOrZero(BillingInvoiceMutationContext context) {
        return amountOrZero(context.getTotalAmount());
    }

    private BigDecimal amountOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String reversalPaymentSource(String originalPaymentSource) {
        if ("COMPANY_BALANCE".equalsIgnoreCase(originalPaymentSource)) {
            return "COMPANY_BALANCE_REVERSAL";
        }
        if ("PAYMOB".equalsIgnoreCase(originalPaymentSource)) {
            return "PAYMOB_REFUND";
        }
        return "MANUAL_REFUND";
    }

    private String refundMetadata(String reference,
                                  String actorUserName,
                                  String note,
                                  BillingPaymentAllocationReversalCandidate candidate) {
        return "{\"source\":\"platform_admin_manual_billing\",\"action\":\"manual_refund\",\"reference\":\"" + escape(reference) +
                "\",\"actorUserName\":\"" + escape(actorUserName) + "\",\"note\":\"" + escape(note) +
                "\",\"reversalOfPaymentId\":" + candidate.getBillingPaymentId() +
                ",\"reversalOfAllocationId\":" + candidate.getBillingPaymentAllocationId() +
                ",\"originalPaymentSource\":\"" + escape(candidate.getPaymentSource()) + "\"}";
    }

    private String json(String action, String actorUserName, String note) {
        return "{\"source\":\"platform_admin_manual_billing\",\"action\":\"" + escape(action) + "\",\"actorUserName\":\"" +
                escape(actorUserName) + "\",\"note\":\"" + escape(note) + "\"}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
