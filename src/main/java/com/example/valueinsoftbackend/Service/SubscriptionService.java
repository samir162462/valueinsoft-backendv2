package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Service.billing.BillingAccountService;
import com.example.valueinsoftbackend.Service.billing.BillingEntitlementService;
import com.example.valueinsoftbackend.Service.billing.BillingInvoicePaymentService;
import com.example.valueinsoftbackend.Service.branch.BranchSubscriptionService;
import com.example.valueinsoftbackend.Service.payment.PaymentAttemptService;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderResolver;
import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbModernSubscription;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import com.example.valueinsoftbackend.Model.Billing.BranchBillingCheckoutCandidate;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationResponse;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SubscriptionService {

    private final DbModernSubscription dbModernSubscription;
    private final DbBillingWriteModels dbBillingWriteModels;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final DbTenants dbTenants;
    private final BillingAccountService billingAccountService;
    private final InvoiceService invoiceService;
    private final PaymentAttemptService paymentAttemptService;
    private final BranchSubscriptionService branchSubscriptionService;
    private final BillingEntitlementService billingEntitlementService;
    private final PaymentProviderResolver paymentProviderResolver;
    private final BillingInvoicePaymentService billingInvoicePaymentService;

    public SubscriptionService(DbModernSubscription dbModernSubscription,
                               DbBillingWriteModels dbBillingWriteModels,
                               DbCompany dbCompany,
                               DbBranch dbBranch,
                               DbTenants dbTenants,
                               BillingAccountService billingAccountService,
                               InvoiceService invoiceService,
                               PaymentAttemptService paymentAttemptService,
                               BranchSubscriptionService branchSubscriptionService,
                               BillingEntitlementService billingEntitlementService,
                               PaymentProviderResolver paymentProviderResolver,
                               BillingInvoicePaymentService billingInvoicePaymentService) {
        this.dbModernSubscription = dbModernSubscription;
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.dbTenants = dbTenants;
        this.billingAccountService = billingAccountService;
        this.invoiceService = invoiceService;
        this.paymentAttemptService = paymentAttemptService;
        this.branchSubscriptionService = branchSubscriptionService;
        this.billingEntitlementService = billingEntitlementService;
        this.paymentProviderResolver = paymentProviderResolver;
        this.billingInvoicePaymentService = billingInvoicePaymentService;
    }

    public List<AppModelSubscription> getBranchSubscription(int branchId) {
        return dbModernSubscription.getBranchSubscriptions(branchId);
    }

    @Transactional
    public String addBranchSubscription(CreateSubscriptionRequest request, String actorUserName) {
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
                Date.valueOf(request.getEndTime()),
                actorUserName
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
        billingEntitlementService.recordPendingPayment(request.getBranchId(), branchSubscriptionId, invoiceId);

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
        Branch branch = dbBranch.getBranchById(branchId);
        TenantConfig tenant = dbTenants.getTenantById(branch.getBranchOfCompanyId());
        if (tenant != null && "suspended".equalsIgnoreCase(tenant.getStatus())) {
            Map<String, Object> details = dbModernSubscription.getBranchActiveState(branchId);
            if (details == null) {
                details = new java.util.HashMap<>();
            }
            details.put("active", false);
            details.put("status", "SUSPENDED");
            details.put("tenantStatus", "suspended");
            details.put("blockReason", "tenant_suspended");
            details.put("message", "Company is suspended by platform admin");
            return details;
        }

        Map<String, Object> details = dbModernSubscription.getBranchActiveState(branchId);
        if (details != null && tenant != null) {
            details.put("tenantStatus", tenant.getStatus());
        }
        return details;
    }

    @Transactional
    public BillingPaymentInitiationResponse initiateBranchPayment(int branchId,
                                                                  BillingPaymentInitiationRequest request,
                                                                  String actorUserName) {
        BranchBillingCheckoutCandidate candidate = requireBranchCheckoutCandidate(branchId);
        return billingInvoicePaymentService.initiatePayment(candidate.getBillingInvoiceId(), request, actorUserName);
    }

    private BranchBillingCheckoutCandidate requireBranchCheckoutCandidate(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        BranchBillingCheckoutCandidate candidate = dbBillingWriteModels.findBranchCheckoutCandidate(branchId);
        if (candidate == null && shouldCreateCheckoutRecoverySubscription(branchId)) {
            createDefaultPayableBranchSubscription(branchId);
            candidate = dbBillingWriteModels.findBranchCheckoutCandidate(branchId);
        }
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
        return candidate;
    }

    private boolean shouldCreateCheckoutRecoverySubscription(int branchId) {
        if (!dbModernSubscription.hasBranchSubscriptionRecords(branchId)) {
            return true;
        }

        Map<String, Object> branchActiveState = dbModernSubscription.getBranchActiveState(branchId);
        return branchActiveState == null || !Boolean.TRUE.equals(branchActiveState.get("active"));
    }

    private long createDefaultPayableBranchSubscription(int branchId) {
        Branch branch = dbBranch.getBranchById(branchId);
        Company company = requireCompany(branch.getBranchOfCompanyId());
        long billingAccountId = billingAccountService.ensureBillingAccount(company);
        BigDecimal amountToPay = resolveDefaultBranchSubscriptionAmount(company);
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(30);

        long branchSubscriptionId = dbModernSubscription.createBranchSubscription(
                billingAccountId,
                company.getCompanyId(),
                branchId,
                company.getPlan(),
                amountToPay,
                Date.valueOf(startDate),
                Date.valueOf(endDate),
                "System"
        );
        long invoiceId = invoiceService.ensureBranchSubscriptionInvoice(
                billingAccountId,
                branchSubscriptionId,
                normalizeCurrency(company.getCurrency()),
                amountToPay,
                BigDecimal.ZERO,
                "Branch subscription for " + branch.getBranchName()
        );
        billingEntitlementService.recordPendingPayment(branchId, branchSubscriptionId, invoiceId);

        log.info(
                "Created default payable branch subscription {} for branch {} during checkout recovery",
                branchSubscriptionId,
                branchId
        );
        return branchSubscriptionId;
    }

    private BigDecimal resolveDefaultBranchSubscriptionAmount(Company company) {
        if (company.getEstablishPrice() > 0) {
            return BigDecimal.valueOf(company.getEstablishPrice());
        }
        return BigDecimal.valueOf(500L);
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
