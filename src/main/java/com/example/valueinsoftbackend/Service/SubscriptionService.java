package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbModernSubscription;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import com.example.valueinsoftbackend.Model.AppModel.BranchBillingCheckoutResponse;
import com.example.valueinsoftbackend.Model.Billing.BranchBillingCheckoutCandidate;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SubscriptionService {

    private final DbModernSubscription dbModernSubscription;
    private final DbBillingWriteModels dbBillingWriteModels;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final BillingAccountService billingAccountService;
    private final InvoiceService invoiceService;
    private final PaymentAttemptService paymentAttemptService;
    private final BranchSubscriptionService branchSubscriptionService;
    private final BillingEntitlementService billingEntitlementService;
    private final PaymentProviderResolver paymentProviderResolver;

    public SubscriptionService(DbModernSubscription dbModernSubscription,
                               DbBillingWriteModels dbBillingWriteModels,
                               DbCompany dbCompany,
                               DbBranch dbBranch,
                               BillingAccountService billingAccountService,
                               InvoiceService invoiceService,
                               PaymentAttemptService paymentAttemptService,
                               BranchSubscriptionService branchSubscriptionService,
                               BillingEntitlementService billingEntitlementService,
                               PaymentProviderResolver paymentProviderResolver) {
        this.dbModernSubscription = dbModernSubscription;
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.billingAccountService = billingAccountService;
        this.invoiceService = invoiceService;
        this.paymentAttemptService = paymentAttemptService;
        this.branchSubscriptionService = branchSubscriptionService;
        this.billingEntitlementService = billingEntitlementService;
        this.paymentProviderResolver = paymentProviderResolver;
    }

    public List<AppModelSubscription> getBranchSubscription(int branchId) {
        return dbModernSubscription.getBranchSubscriptions(branchId);
    }

    @Transactional
    public String addBranchSubscription(CreateSubscriptionRequest request) {
        validateSubscription(request);
        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        Branch branch = dbBranch.getBranchById(request.getBranchId());
        Company company = requireCompany(branch.getBranchOfCompanyId());
        long billingAccountId = billingAccountService.ensureBillingAccount(company);
        BigDecimal initialAmountPaid = request.getAmountPaid() == null ? BigDecimal.ZERO : request.getAmountPaid();
        long branchSubscriptionId = dbModernSubscription.createBranchSubscription(
                billingAccountId,
                company.getCompanyId(),
                request.getBranchId(),
                company.getPlan(),
                request.getAmountToPay(),
                Date.valueOf(request.getStartTime()),
                Date.valueOf(request.getEndTime())
        );
        long invoiceId = invoiceService.ensureBranchSubscriptionInvoice(
                billingAccountId,
                branchSubscriptionId,
                normalizeCurrency(company.getCurrency()),
                request.getAmountToPay(),
                initialAmountPaid,
                "Branch subscription for " + branch.getBranchName()
        );
        int providerOrderId = paymentProvider.createProviderOrder((int) branchSubscriptionId, request.getBranchId(), request.getAmountToPay());
        paymentAttemptService.ensureCreatedAttempt(
                invoiceId,
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                request.getAmountToPay(),
                normalizeCurrency(company.getCurrency()),
                "{\"branchSubscriptionId\":" + branchSubscriptionId + ",\"branchId\":" + request.getBranchId() + "}",
                "{\"providerOrderId\":" + providerOrderId + "}"
        );
        billingEntitlementService.recordPendingPayment(request.getBranchId(), branchSubscriptionId, invoiceId, 0);

        log.info(
                "Created modern branch subscription {} for branch {} with provider {} order {}",
                branchSubscriptionId,
                request.getBranchId(),
                paymentProvider.getProviderCode(),
                providerOrderId
        );
        return "the Add BranchSubscription Added Successfully : " + request.getBranchId();
    }

    @Transactional
    public void markBranchSubscriptionStatusSuccess(int orderId) {
        TenantSqlIdentifiers.requirePositive(orderId, "orderId");
        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        String externalOrderId = String.valueOf(orderId);
        paymentAttemptService.markSucceeded(
                paymentProvider.getProviderCode(),
                externalOrderId,
                "{\"orderId\":" + orderId + "}",
                externalOrderId
        );
        invoiceService.markPaidByExternalOrderId(paymentProvider.getProviderCode(), externalOrderId);
        branchSubscriptionService.markPaidByExternalOrderId(paymentProvider.getProviderCode(), externalOrderId);
        Integer branchId = dbModernSubscription.getBranchIdByExternalOrderId(paymentProvider.getProviderCode(), externalOrderId);
        if (branchId != null) {
            billingEntitlementService.recordActivated(branchId, externalOrderId);
        }
        log.info("Marked modern branch subscription as paid for provider order {}", orderId);
    }

    public Map<String, Object> isActive(int branchId) {
        return dbModernSubscription.getBranchActiveState(branchId);
    }

    @Transactional
    public BranchBillingCheckoutResponse createBranchCheckout(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        BranchBillingCheckoutCandidate candidate = dbBillingWriteModels.findBranchCheckoutCandidate(branchId);
        if (candidate == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_BILLING_CHECKOUT_NOT_AVAILABLE",
                    "No payable branch billing record is available for checkout"
            );
        }
        if (!"open".equalsIgnoreCase(candidate.getInvoiceStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_BILLING_INVOICE_NOT_OPEN",
                    "Only open branch billing invoices can start checkout"
            );
        }
        if (candidate.getDueAmount() == null || candidate.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_BILLING_AMOUNT_NOT_DUE",
                    "No unpaid branch billing amount is available for checkout"
            );
        }

        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        String externalOrderId = candidate.getLatestExternalOrderId();
        if (shouldCreateNewProviderOrder(candidate, externalOrderId)) {
            int providerOrderId = paymentProvider.createProviderOrder(
                    generateCheckoutMerchantOrderId(candidate),
                    candidate.getBranchId(),
                    candidate.getDueAmount()
            );
            externalOrderId = String.valueOf(providerOrderId);
            paymentAttemptService.ensureCreatedAttempt(
                    candidate.getBillingInvoiceId(),
                    paymentProvider.getProviderCode(),
                    externalOrderId,
                    candidate.getDueAmount(),
                    normalizeCurrency(candidate.getCurrencyCode()),
                    "{\"branchSubscriptionId\":" + candidate.getBranchSubscriptionId() + ",\"branchId\":" + candidate.getBranchId() + ",\"source\":\"branch_checkout\"}",
                    "{\"providerOrderId\":" + externalOrderId + ",\"source\":\"branch_checkout\"}"
            );
        }

        PaymentTokenRequest request = new PaymentTokenRequest();
        request.setOrderId(Long.parseLong(externalOrderId));
        request.setBranchId(candidate.getBranchId());
        request.setCompanyId(candidate.getCompanyId());
        request.setCurrency(normalizeCurrency(candidate.getCurrencyCode()));
        request.setAmountCents(candidate.getDueAmount().multiply(BigDecimal.valueOf(100L)).longValue());

        String checkoutUrl = paymentProvider.createPaymentKeyUrl(request);
        paymentAttemptService.markCheckoutRequested(
                paymentProvider.getProviderCode(),
                externalOrderId,
                "{\"checkoutUrl\":\"" + checkoutUrl + "\",\"source\":\"branch_checkout\"}"
        );

        return new BranchBillingCheckoutResponse(
                candidate.getBillingInvoiceId(),
                candidate.getBranchSubscriptionId(),
                paymentProvider.getProviderCode(),
                externalOrderId,
                checkoutUrl,
                candidate.getDueAmount(),
                normalizeCurrency(candidate.getCurrencyCode())
        );
    }

    private boolean shouldCreateNewProviderOrder(BranchBillingCheckoutCandidate candidate, String externalOrderId) {
        if (externalOrderId == null || externalOrderId.isBlank() || "0".equals(externalOrderId.trim())) {
            return true;
        }

        String latestStatus = candidate.getLatestAttemptStatus();
        return latestStatus == null
                || latestStatus.isBlank()
                || "failed".equalsIgnoreCase(latestStatus);
    }

    private int generateCheckoutMerchantOrderId(BranchBillingCheckoutCandidate candidate) {
        long suffix = System.currentTimeMillis() % 1_000_000L;
        long merchantOrderId = (long) candidate.getBranchId() * 1_000_000L + suffix;
        if (merchantOrderId <= Integer.MAX_VALUE) {
            return (int) merchantOrderId;
        }

        return (int) ((merchantOrderId % 1_000_000_000L) + 100_000_000L);
    }

    private void validateSubscription(CreateSubscriptionRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_DATE_RANGE_INVALID", "endTime must be on or after startTime");
        }
        if (request.getAmountPaid() != null && request.getAmountPaid().compareTo(request.getAmountToPay()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_AMOUNT_INVALID", "amountPaid cannot be greater than amountToPay");
        }
    }

    private Company requireCompany(int companyId) {
        Company company = dbCompany.getCompanyById(companyId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return company;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "EGP";
        }

        String normalized = currency.trim();
        if ("le".equalsIgnoreCase(normalized) || "egp".equalsIgnoreCase(normalized)) {
            return "EGP";
        }

        return normalized.toUpperCase();
    }
}
