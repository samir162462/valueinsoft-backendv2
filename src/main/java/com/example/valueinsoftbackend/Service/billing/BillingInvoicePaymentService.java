package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceSettlementSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentPreviewResponse;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;

@Service
public class BillingInvoicePaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final DbBillingWriteModels dbBillingWriteModels;
    private final BillingEntitlementService billingEntitlementService;
    private final PaymentProviderResolver paymentProviderResolver;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public BillingInvoicePaymentService(DbBillingWriteModels dbBillingWriteModels,
                                        BillingEntitlementService billingEntitlementService,
                                        PaymentProviderResolver paymentProviderResolver,
                                        FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.billingEntitlementService = billingEntitlementService;
        this.paymentProviderResolver = paymentProviderResolver;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    public BillingPaymentPreviewResponse previewPayment(long billingInvoiceId) {
        BillingInvoicePaymentContext context = requirePaymentContext(billingInvoiceId, false);
        BigDecimal dueAmount = amount(context.getDueAmount());
        BigDecimal availableBalance = amount(context.getAvailableBalance());
        BigDecimal balanceAppliedAmount = min(dueAmount, availableBalance);
        BigDecimal providerAmountDue = dueAmount.subtract(balanceAppliedAmount).max(ZERO);
        return previewResponse(context, balanceAppliedAmount, providerAmountDue);
    }

    public BillingInvoicePaymentContext requireAuthorizationContext(long billingInvoiceId) {
        return requirePaymentContext(billingInvoiceId, false);
    }

    @Transactional
    public BillingPaymentInitiationResponse initiatePayment(long billingInvoiceId,
                                                            BillingPaymentInitiationRequest request) {
        return initiatePayment(billingInvoiceId, request, true);
    }

    @Transactional
    public BillingPaymentInitiationResponse settleFromBalance(long billingInvoiceId,
                                                              BillingPaymentInitiationRequest request) {
        return initiatePayment(billingInvoiceId, request, false);
    }

    private BillingPaymentInitiationResponse initiatePayment(long billingInvoiceId,
                                                             BillingPaymentInitiationRequest request,
                                                             boolean allowCheckoutFallback) {
        BillingInvoicePaymentContext context = requirePaymentContext(billingInvoiceId, true);
        String idempotencyKey = requireIdempotencyKey(request);
        BillingBalanceSettlementSnapshot existingSettlement =
                dbBillingWriteModels.findBalanceSettlementByIdempotencyKey(context.getCompanyId(), billingInvoiceId, idempotencyKey);
        BillingPaymentAttemptSnapshot existingAttempt =
                dbBillingWriteModels.findPaymentAttemptByCompanyIdempotency(context.getCompanyId(), idempotencyKey);
        if (existingSettlement != null || existingAttempt != null) {
            return idempotentResponse(context, existingSettlement, existingAttempt, allowCheckoutFallback);
        }

        BigDecimal dueAmount = amount(context.getDueAmount());
        if (dueAmount.compareTo(ZERO) <= 0) {
            return response(
                    context,
                    "ALREADY_PAID",
                    normalizeInvoiceStatus(context.getInvoiceStatus()),
                    ZERO,
                    ZERO,
                    ZERO,
                    amount(context.getAvailableBalance()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        if (isTerminalInvoiceStatus(context.getInvoiceStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_INVOICE_NOT_PAYABLE", "Invoice is not payable");
        }

        BigDecimal balanceAppliedAmount = min(dueAmount, amount(context.getAvailableBalance()));
        SettlementIds settlementIds = SettlementIds.empty();
        BigDecimal paidAmount = amount(context.getPaidAmount());
        BigDecimal remainingDueAmount = dueAmount;
        BigDecimal availableBalance = amount(context.getAvailableBalance());

        if (balanceAppliedAmount.compareTo(ZERO) > 0) {
            settlementIds = applyBalanceSettlement(context, balanceAppliedAmount, idempotencyKey);
            paidAmount = paidAmount.add(balanceAppliedAmount);
            remainingDueAmount = dueAmount.subtract(balanceAppliedAmount).max(ZERO);
            availableBalance = availableBalance.subtract(balanceAppliedAmount).max(ZERO);
            updateInvoiceAfterSettlement(context, paidAmount, remainingDueAmount);
            enqueueInternalBalancePosting(context, settlementIds, balanceAppliedAmount);
        }

        if (remainingDueAmount.compareTo(ZERO) == 0) {
            activateBranchSubscriptionIfNeeded(context, idempotencyKey);
            return response(
                    context,
                    "PAID_FROM_BALANCE",
                    "paid",
                    balanceAppliedAmount,
                    ZERO,
                    ZERO,
                    availableBalance,
                    settlementIds.paymentId(),
                    settlementIds.allocationId(),
                    settlementIds.ledgerId(),
                    null,
                    null,
                    null,
                    null
            );
        }

        if (!allowCheckoutFallback || Boolean.FALSE.equals(request == null ? null : request.getCheckoutFallbackEnabled())) {
            return response(
                    context,
                    balanceAppliedAmount.compareTo(ZERO) > 0 ? "BALANCE_PARTIALLY_APPLIED" : "BALANCE_NOT_AVAILABLE",
                    "open",
                    balanceAppliedAmount,
                    remainingDueAmount,
                    remainingDueAmount,
                    availableBalance,
                    settlementIds.paymentId(),
                    settlementIds.allocationId(),
                    settlementIds.ledgerId(),
                    null,
                    null,
                    null,
                    null
            );
        }

        ProviderCheckout providerCheckout = createProviderCheckout(context, remainingDueAmount, idempotencyKey);
        return response(
                context,
                balanceAppliedAmount.compareTo(ZERO) > 0 ? "PARTIAL_BALANCE_CHECKOUT_REQUIRED" : "CHECKOUT_REQUIRED",
                "open",
                balanceAppliedAmount,
                remainingDueAmount,
                remainingDueAmount,
                availableBalance,
                settlementIds.paymentId(),
                settlementIds.allocationId(),
                settlementIds.ledgerId(),
                providerCheckout.paymentAttemptId(),
                providerCheckout.providerCode(),
                providerCheckout.externalOrderId(),
                providerCheckout.checkoutUrl()
        );
    }

    private SettlementIds applyBalanceSettlement(BillingInvoicePaymentContext context,
                                                 BigDecimal amount,
                                                 String idempotencyKey) {
        BigDecimal balanceBefore = amount(context.getAvailableBalance());
        BigDecimal balanceAfter = balanceBefore.subtract(amount).max(ZERO);
        String currencyCode = normalizeCurrency(context.getCurrencyCode());
        String metadataJson = "{\"source\":\"billing_balance_first\",\"billingInvoiceId\":" + context.getBillingInvoiceId() + "}";

        dbBillingWriteModels.updateBillingAccountAvailableBalance(context.getBillingAccountId(), balanceAfter);
        long ledgerId = dbBillingWriteModels.createBillingAccountLedgerEntry(
                context.getBillingAccountId(),
                context.getCompanyId(),
                "INVOICE_BALANCE_SETTLEMENT",
                amount,
                currencyCode,
                "DEBIT",
                balanceBefore,
                balanceAfter,
                "billing_invoice",
                String.valueOf(context.getBillingInvoiceId()),
                idempotencyKey,
                null,
                null,
                null,
                "Applied account balance to invoice " + context.getBillingInvoiceId(),
                metadataJson
        );
        long paymentId = dbBillingWriteModels.createBillingPayment(
                context.getCompanyId(),
                context.getBillingAccountId(),
                "COMPANY_BALANCE",
                null,
                amount,
                currencyCode,
                "ALLOCATED",
                "BALANCE-" + context.getBillingInvoiceId(),
                idempotencyKey,
                metadataJson
        );
        long allocationId = dbBillingWriteModels.createBillingPaymentAllocation(
                paymentId,
                context.getBillingInvoiceId(),
                amount,
                currencyCode
        );
        return new SettlementIds(paymentId, allocationId, ledgerId);
    }

    private void enqueueInternalBalancePosting(BillingInvoicePaymentContext context,
                                               SettlementIds settlementIds,
                                               BigDecimal amount) {
        if (settlementIds.paymentId() == null || settlementIds.allocationId() == null) {
            return;
        }
        financeOperationalPostingService.enqueueBillingBalanceSettlement(
                context.getCompanyId(),
                context.getBranchId(),
                context.getBillingInvoiceId(),
                settlementIds.paymentId(),
                settlementIds.allocationId(),
                amount,
                normalizeCurrency(context.getCurrencyCode()),
                new Timestamp(System.currentTimeMillis()),
                "system"
        );
    }

    private void updateInvoiceAfterSettlement(BillingInvoicePaymentContext context,
                                              BigDecimal paidAmount,
                                              BigDecimal remainingDueAmount) {
        boolean fullyPaid = remainingDueAmount.compareTo(ZERO) == 0;
        dbBillingWriteModels.updateInvoicePaymentProjection(
                context.getBillingInvoiceId(),
                fullyPaid ? "paid" : "open",
                paidAmount,
                remainingDueAmount,
                fullyPaid ? Instant.now() : null,
                "{\"source\":\"billing_balance_first\",\"balanceProjection\":\"paid_amount_plus_allocations\"}"
        );
    }

    private ProviderCheckout createProviderCheckout(BillingInvoicePaymentContext context,
                                                    BigDecimal providerAmountDue,
                                                    String idempotencyKey) {
        if (context.getBranchId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BILLING_INVOICE_CHECKOUT_BRANCH_REQUIRED",
                    "Checkout fallback requires a branch subscription invoice"
            );
        }

        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        dbBillingWriteModels.supersedeActivePaymentAttempts(context.getBillingInvoiceId(), paymentProvider.getProviderCode());

        int providerOrderId = paymentProvider.createProviderOrder(
                generateMerchantOrderId(context),
                context.getBranchId(),
                providerAmountDue
        );
        String externalOrderId = String.valueOf(providerOrderId);
        PaymentTokenRequest tokenRequest = new PaymentTokenRequest();
        tokenRequest.setOrderId(providerOrderId);
        tokenRequest.setBranchId(context.getBranchId());
        tokenRequest.setCompanyId(context.getCompanyId());
        tokenRequest.setCurrency(normalizeCurrency(context.getCurrencyCode()));
        tokenRequest.setAmountCents(providerAmountDue.multiply(BigDecimal.valueOf(100L)).longValue());
        String checkoutUrl = paymentProvider.createPaymentKeyUrl(tokenRequest);

        long attemptId = dbBillingWriteModels.createPaymentAttempt(
                context.getBillingInvoiceId(),
                context.getCompanyId(),
                context.getBranchId(),
                idempotencyKey,
                paymentProvider.getProviderCode(),
                externalOrderId,
                "CHECKOUT_REQUESTED",
                providerAmountDue,
                normalizeCurrency(context.getCurrencyCode()),
                externalOrderId,
                "{\"source\":\"billing_initiate_payment\",\"billingInvoiceId\":" + context.getBillingInvoiceId() + "}",
                "{\"checkoutUrl\":\"" + escapeJson(checkoutUrl) + "\",\"source\":\"billing_initiate_payment\"}",
                "{\"source\":\"billing_initiate_payment\",\"fallback\":\"provider_checkout\"}"
        );
        return new ProviderCheckout(attemptId, paymentProvider.getProviderCode(), externalOrderId, checkoutUrl);
    }

    private void activateBranchSubscriptionIfNeeded(BillingInvoicePaymentContext context, String reference) {
        if (context.getBranchSubscriptionId() == null || context.getBranchId() == null) {
            return;
        }
        dbBillingWriteModels.updateBranchSubscriptionStatusById(
                context.getBranchSubscriptionId(),
                "active",
                "{\"source\":\"billing_balance_first\",\"reference\":\"" + escapeJson(reference) + "\"}"
        );
        billingEntitlementService.recordManualStateChange(
                context.getBranchId(),
                context.getBranchSubscriptionId(),
                context.getBillingInvoiceId(),
                "pending_payment",
                "active",
                "balance_payment",
                "{\"source\":\"billing_balance_first\",\"reference\":\"" + escapeJson(reference) + "\"}"
        );
    }

    private BillingInvoicePaymentContext requirePaymentContext(long billingInvoiceId, boolean lock) {
        BillingInvoicePaymentContext context = lock
                ? dbBillingWriteModels.lockInvoicePaymentContext(billingInvoiceId)
                : dbBillingWriteModels.findInvoicePaymentContext(billingInvoiceId);
        if (context == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BILLING_INVOICE_NOT_FOUND", "Billing invoice not found");
        }
        return context;
    }

    private BillingPaymentPreviewResponse previewResponse(BillingInvoicePaymentContext context,
                                                          BigDecimal balanceAppliedAmount,
                                                          BigDecimal providerAmountDue) {
        return new BillingPaymentPreviewResponse(
                context.getBillingInvoiceId(),
                context.getCompanyId(),
                context.getBranchId(),
                normalizeInvoiceStatus(context.getInvoiceStatus()),
                normalizeCurrency(context.getCurrencyCode()),
                amount(context.getTotalAmount()),
                amount(context.getPaidAmount()),
                amount(context.getDueAmount()),
                amount(context.getAvailableBalance()),
                balanceAppliedAmount,
                providerAmountDue,
                resolvePaymentStrategy(balanceAppliedAmount, providerAmountDue)
        );
    }

    private BillingPaymentInitiationResponse response(BillingInvoicePaymentContext context,
                                                      String status,
                                                      String invoiceStatus,
                                                      BigDecimal balanceAppliedAmount,
                                                      BigDecimal providerAmountDue,
                                                      BigDecimal remainingDueAmount,
                                                      BigDecimal availableBalance,
                                                      Long billingPaymentId,
                                                      Long billingPaymentAllocationId,
                                                      Long billingAccountLedgerId,
                                                      Long billingPaymentAttemptId,
                                                      String providerCode,
                                                      String externalOrderId,
                                                      String checkoutUrl) {
        return new BillingPaymentInitiationResponse(
                context.getBillingInvoiceId(),
                context.getCompanyId(),
                context.getBranchId(),
                status,
                invoiceStatus,
                normalizeCurrency(context.getCurrencyCode()),
                amount(balanceAppliedAmount),
                amount(providerAmountDue),
                amount(remainingDueAmount),
                amount(availableBalance),
                billingPaymentId,
                billingPaymentAllocationId,
                billingAccountLedgerId,
                billingPaymentAttemptId,
                providerCode,
                externalOrderId,
                checkoutUrl
        );
    }

    private BillingPaymentInitiationResponse idempotentResponse(BillingInvoicePaymentContext context,
                                                                BillingBalanceSettlementSnapshot existingSettlement,
                                                                BillingPaymentAttemptSnapshot existingAttempt,
                                                                boolean allowCheckoutFallback) {
        BigDecimal balanceAppliedAmount = existingSettlement == null ? ZERO : amount(existingSettlement.getAmount());
        BigDecimal remainingDueAmount = amount(context.getDueAmount());
        BigDecimal providerAmountDue = existingAttempt == null ? remainingDueAmount : amount(existingAttempt.getRequestedAmount());
        String status;
        if (remainingDueAmount.compareTo(ZERO) == 0) {
            status = "PAID_FROM_BALANCE";
            providerAmountDue = ZERO;
        } else if (existingAttempt != null && allowCheckoutFallback) {
            status = balanceAppliedAmount.compareTo(ZERO) > 0 ? "PARTIAL_BALANCE_CHECKOUT_REQUIRED" : "CHECKOUT_REQUIRED";
        } else {
            status = balanceAppliedAmount.compareTo(ZERO) > 0 ? "BALANCE_PARTIALLY_APPLIED" : "BALANCE_NOT_AVAILABLE";
        }

        return response(
                context,
                status,
                normalizeInvoiceStatus(context.getInvoiceStatus()),
                balanceAppliedAmount,
                providerAmountDue,
                remainingDueAmount,
                amount(context.getAvailableBalance()),
                existingSettlement == null ? null : existingSettlement.getBillingPaymentId(),
                existingSettlement == null ? null : existingSettlement.getBillingPaymentAllocationId(),
                existingSettlement == null ? null : existingSettlement.getBillingAccountLedgerId(),
                existingAttempt == null ? null : existingAttempt.getBillingPaymentAttemptId(),
                existingAttempt == null ? null : existingAttempt.getProviderCode(),
                existingAttempt == null ? null : existingAttempt.getExternalOrderId(),
                existingAttempt == null ? null : existingAttempt.getCheckoutUrl()
        );
    }

    private String resolvePaymentStrategy(BigDecimal balanceAppliedAmount, BigDecimal providerAmountDue) {
        if (providerAmountDue.compareTo(ZERO) == 0 && balanceAppliedAmount.compareTo(ZERO) > 0) {
            return "BALANCE_ONLY";
        }
        if (balanceAppliedAmount.compareTo(ZERO) > 0) {
            return "BALANCE_THEN_CHECKOUT";
        }
        return providerAmountDue.compareTo(ZERO) > 0 ? "CHECKOUT_ONLY" : "NO_PAYMENT_DUE";
    }

    private boolean isTerminalInvoiceStatus(String status) {
        String normalized = normalizeInvoiceStatus(status);
        return "paid".equals(normalized) || "refunded".equals(normalized) || "void".equals(normalized);
    }

    private String requireIdempotencyKey(BillingPaymentInitiationRequest request) {
        if (request != null && request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            return request.getIdempotencyKey().trim();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
    }

    private int generateMerchantOrderId(BillingInvoicePaymentContext context) {
        long suffix = System.currentTimeMillis() % 1_000_000L;
        int branchId = context.getBranchId() == null ? context.getCompanyId() : context.getBranchId();
        long merchantOrderId = (long) branchId * 1_000_000L + suffix;
        if (merchantOrderId <= Integer.MAX_VALUE) {
            return (int) merchantOrderId;
        }
        return (int) ((merchantOrderId % 1_000_000_000L) + 100_000_000L);
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeInvoiceStatus(String status) {
        return status == null || status.isBlank() ? "open" : status.trim().toLowerCase(Locale.ROOT);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record SettlementIds(Long paymentId, Long allocationId, Long ledgerId) {
        private static SettlementIds empty() {
            return new SettlementIds(null, null, null);
        }
    }

    private record ProviderCheckout(Long paymentAttemptId, String providerCode, String externalOrderId, String checkoutUrl) {
    }
}
