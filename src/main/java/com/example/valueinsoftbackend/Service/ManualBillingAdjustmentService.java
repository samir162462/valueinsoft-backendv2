package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoiceMutationContext;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingManualActionResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.ManualBillingActionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;

@Service
public class ManualBillingAdjustmentService {

    private final DbBillingWriteModels dbBillingWriteModels;
    private final BillingEntitlementService billingEntitlementService;

    public ManualBillingAdjustmentService(DbBillingWriteModels dbBillingWriteModels,
                                          BillingEntitlementService billingEntitlementService) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.billingEntitlementService = billingEntitlementService;
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
        dbBillingWriteModels.updateInvoiceManualState(
                billingInvoiceId,
                "refunded",
                invoiceTotal,
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

        return response(context, "manual_refund", "refunded", "refunded", amount, invoiceTotal,
                "manual_refund", reference, attemptId);
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
        return context.getTotalAmount() == null ? BigDecimal.ZERO : context.getTotalAmount();
    }

    private String json(String action, String actorUserName, String note) {
        return "{\"source\":\"platform_admin_manual_billing\",\"action\":\"" + escape(action) + "\",\"actorUserName\":\"" +
                escape(actorUserName) + "\",\"note\":\"" + escape(note) + "\"}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
