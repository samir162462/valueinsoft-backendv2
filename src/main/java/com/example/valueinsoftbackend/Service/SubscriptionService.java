package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbModernSubscription;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
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
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final BillingAccountService billingAccountService;
    private final InvoiceService invoiceService;
    private final PaymentAttemptService paymentAttemptService;
    private final BranchSubscriptionService branchSubscriptionService;
    private final BillingEntitlementService billingEntitlementService;
    private final PaymentProviderResolver paymentProviderResolver;

    public SubscriptionService(DbModernSubscription dbModernSubscription,
                               DbCompany dbCompany,
                               DbBranch dbBranch,
                               BillingAccountService billingAccountService,
                               InvoiceService invoiceService,
                               PaymentAttemptService paymentAttemptService,
                               BranchSubscriptionService branchSubscriptionService,
                               BillingEntitlementService billingEntitlementService,
                               PaymentProviderResolver paymentProviderResolver) {
        this.dbModernSubscription = dbModernSubscription;
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
        return currency == null || currency.isBlank() ? "EGP" : currency.trim();
    }
}
