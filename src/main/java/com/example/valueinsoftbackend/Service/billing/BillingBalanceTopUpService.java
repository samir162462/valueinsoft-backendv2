package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceTopUpRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceTopUpResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptSnapshot;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Service.payment.PaymentAttemptService;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;

/**
 * Lets a company put money on its billing balance ("top up") through the online
 * payment provider, mirroring the branch billing checkout flow. The provider
 * webhook credits the balance once the payment settles.
 */
@Service
@Slf4j
public class BillingBalanceTopUpService {

    public static final String TOP_UP_SOURCE_TYPE = "balance_top_up";

    private final DbBillingWriteModels dbBillingWriteModels;
    private final DbCompany dbCompany;
    private final BillingAccountService billingAccountService;
    private final PaymentProviderResolver paymentProviderResolver;
    private final PaymentAttemptService paymentAttemptService;

    public BillingBalanceTopUpService(DbBillingWriteModels dbBillingWriteModels,
                                      DbCompany dbCompany,
                                      BillingAccountService billingAccountService,
                                      PaymentProviderResolver paymentProviderResolver,
                                      PaymentAttemptService paymentAttemptService) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.dbCompany = dbCompany;
        this.billingAccountService = billingAccountService;
        this.paymentProviderResolver = paymentProviderResolver;
        this.paymentAttemptService = paymentAttemptService;
    }

    @Transactional
    public BillingBalanceTopUpResponse initiateTopUp(int companyId,
                                                     int branchId,
                                                     BillingBalanceTopUpRequest request,
                                                     String actorUserName) {
        validate(request);
        Company company = dbCompany.getCompanyById(companyId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        long billingAccountId = billingAccountService.ensureBillingAccount(company);
        String idempotencyKey = request.getIdempotencyKey().trim();
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        Long existingInvoiceId = dbBillingWriteModels.findInvoiceIdBySource(TOP_UP_SOURCE_TYPE, idempotencyKey);
        if (existingInvoiceId != null) {
            BillingPaymentAttemptSnapshot attempt =
                    dbBillingWriteModels.findLatestPaymentAttemptByInvoiceId(existingInvoiceId);
            return new BillingBalanceTopUpResponse(
                    companyId,
                    existingInvoiceId,
                    attempt == null ? null : attempt.getBillingPaymentAttemptId(),
                    attempt == null ? null : attempt.getProviderCode(),
                    attempt == null ? null : attempt.getExternalOrderId(),
                    attempt == null ? null : attempt.getCheckoutUrl(),
                    attempt == null || attempt.getRequestedAmount() == null ? amount : attempt.getRequestedAmount(),
                    "EGP",
                    "ALREADY_REQUESTED"
            );
        }

        long invoiceId = dbBillingWriteModels.createInvoice(
                billingAccountId,
                "TOPUP-" + companyId + "-" + System.currentTimeMillis(),
                "open",
                "EGP",
                amount,
                amount,
                amount,
                new Timestamp(System.currentTimeMillis()),
                TOP_UP_SOURCE_TYPE,
                idempotencyKey,
                "{\"source\":\"billing_balance_topup\",\"companyId\":" + companyId
                        + ",\"branchId\":" + branchId
                        + ",\"actorUserName\":\"" + escapeJson(actorUserName) + "\""
                        + ",\"note\":\"" + escapeJson(request.getNote()) + "\"}"
        );

        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        // Offset keeps top-up merchant order ids out of the branch-subscription id space,
        // since the provider requires merchant_order_id to be unique per merchant.
        int merchantOrderId = Math.toIntExact(900_000_000L + invoiceId);
        int providerOrderId = paymentProvider.createProviderOrder(
                merchantOrderId,
                branchId,
                amount
        );

        long paymentAttemptId = paymentAttemptService.ensureCreatedAttempt(
                invoiceId,
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                amount,
                "EGP",
                "{\"source\":\"billing_balance_topup\",\"companyId\":" + companyId + ",\"branchId\":" + branchId + "}",
                "{\"providerOrderId\":" + providerOrderId + "}"
        );

        PaymentTokenRequest tokenRequest = new PaymentTokenRequest();
        tokenRequest.setOrderId(providerOrderId);
        tokenRequest.setBranchId(branchId);
        tokenRequest.setCompanyId(companyId);
        tokenRequest.setCurrency("EGP");
        tokenRequest.setAmountCents(amount.multiply(BigDecimal.valueOf(100L)).longValue());
        String checkoutUrl = paymentProvider.createPaymentKeyUrl(tokenRequest);
        paymentAttemptService.markCheckoutRequested(
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                "{\"checkoutUrl\":\"" + escapeJson(checkoutUrl) + "\",\"source\":\"billing_balance_topup\"}"
        );

        log.info("Balance top-up checkout created for company {} (invoice {}, amount {})", companyId, invoiceId, amount);
        return new BillingBalanceTopUpResponse(
                companyId,
                invoiceId,
                paymentAttemptId,
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                checkoutUrl,
                amount,
                "EGP",
                "CHECKOUT_CREATED"
        );
    }

    private void validate(BillingBalanceTopUpRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_TOPUP_REQUEST_REQUIRED", "Top-up request is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_TOPUP_AMOUNT_INVALID", "Top-up amount must be positive");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
