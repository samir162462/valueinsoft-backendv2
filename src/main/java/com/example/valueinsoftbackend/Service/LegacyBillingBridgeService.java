package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Map;

@Service
@Slf4j
public class LegacyBillingBridgeService {

    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final DbBillingWriteModels dbBillingWriteModels;
    private final BillingAccountService billingAccountService;
    private final BranchSubscriptionService branchSubscriptionService;
    private final InvoiceService invoiceService;
    private final PaymentAttemptService paymentAttemptService;
    private final BillingProviderEventService billingProviderEventService;
    private final BillingEntitlementService billingEntitlementService;
    private final ObjectMapper objectMapper;

    public LegacyBillingBridgeService(DbCompany dbCompany,
                                      DbBranch dbBranch,
                                      DbBillingWriteModels dbBillingWriteModels,
                                      BillingAccountService billingAccountService,
                                      BranchSubscriptionService branchSubscriptionService,
                                      InvoiceService invoiceService,
                                      PaymentAttemptService paymentAttemptService,
                                      BillingProviderEventService billingProviderEventService,
                                      BillingEntitlementService billingEntitlementService,
                                      ObjectMapper objectMapper) {
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.billingAccountService = billingAccountService;
        this.branchSubscriptionService = branchSubscriptionService;
        this.invoiceService = invoiceService;
        this.paymentAttemptService = paymentAttemptService;
        this.billingProviderEventService = billingProviderEventService;
        this.billingEntitlementService = billingEntitlementService;
        this.objectMapper = objectMapper;
    }

    public void syncSubscriptionCreation(int legacySubscriptionId,
                                         CreateSubscriptionRequest request,
                                         String providerCode,
                                         int providerOrderId) {
        try {
            Branch branch = dbBranch.getBranchById(request.getBranchId());
            Company company = requireCompany(branch.getBranchOfCompanyId());
            syncLegacySnapshot(
                    company,
                    branch,
                    legacySubscriptionId,
                    Date.valueOf(request.getStartTime()),
                    Date.valueOf(request.getEndTime()),
                    request.getAmountToPay(),
                    request.getAmountPaid() == null ? BigDecimal.ZERO : request.getAmountPaid(),
                    providerCode,
                    providerOrderId,
                    "NP"
            );
        } catch (Exception exception) {
            log.error("Billing bridge sync failed for legacy subscription {}", legacySubscriptionId, exception);
        }
    }

    public boolean repairLegacyMirror(int tenantId,
                                      int branchId,
                                      int legacySubscriptionId,
                                      Date startTime,
                                      Date endTime,
                                      BigDecimal amountToPay,
                                      BigDecimal amountPaid,
                                      Integer legacyOrderId,
                                      String legacyStatus,
                                      String providerCode) {
        try {
            Company company = requireCompany(tenantId);
            Branch branch = dbBranch.getBranchById(branchId);
            syncLegacySnapshot(
                    company,
                    branch,
                    legacySubscriptionId,
                    startTime,
                    endTime,
                    amountToPay,
                    amountPaid,
                    providerCode == null || providerCode.isBlank() ? "paymob" : providerCode,
                    legacyOrderId == null ? 0 : legacyOrderId,
                    legacyStatus == null ? "NP" : legacyStatus
            );
            return true;
        } catch (Exception exception) {
            log.error("Billing mirror repair failed for legacy subscription {}", legacySubscriptionId, exception);
            return false;
        }
    }

    public void syncCheckoutRequest(String providerCode,
                                    PaymentTokenRequest request,
                                    String checkoutUrl) {
        try {
            paymentAttemptService.markCheckoutRequested(
                    providerCode,
                    String.valueOf(request.getOrderId()),
                    toJson(Map.of(
                            "checkoutUrl", checkoutUrl,
                            "branchId", request.getBranchId(),
                            "companyId", request.getCompanyId()
                    ))
            );
        } catch (Exception exception) {
            log.error("Billing bridge checkout sync failed for external order {}", request.getOrderId(), exception);
        }
    }

    public void syncCallback(String providerCode,
                             PayMobTransactionCallbackRequest request,
                             TransactionProcessedCallback callback) {
        String providerEventId = String.valueOf(request.getTransaction().getId());
        String externalOrderId = String.valueOf(callback.getSubId());
        String payloadJson = toJson(request);

        try {
            billingProviderEventService.recordProcessedEvent(
                    providerCode,
                    providerEventId,
                    "transaction_callback",
                    externalOrderId,
                    payloadJson
            );

            if (callback.isSuccess()) {
                paymentAttemptService.markSucceeded(
                        providerCode,
                        externalOrderId,
                        payloadJson,
                        String.valueOf(callback.getOrder_id())
                );
                invoiceService.markPaidByExternalOrderId(providerCode, externalOrderId);
                branchSubscriptionService.markPaidByExternalOrderId(providerCode, externalOrderId);

                Integer branchId = dbBillingWriteModels.findBranchIdByExternalOrderId(providerCode, externalOrderId);
                if (branchId != null) {
                    billingEntitlementService.recordActivated(branchId, externalOrderId);
                }
            } else {
                paymentAttemptService.markFailed(
                        providerCode,
                        externalOrderId,
                        payloadJson,
                        "PAYMENT_NOT_SUCCESSFUL",
                        "Provider callback marked the transaction as not successful",
                        String.valueOf(callback.getOrder_id())
                );
            }
        } catch (Exception exception) {
            log.error("Billing bridge callback sync failed for provider event {}", providerEventId, exception);
            try {
                billingProviderEventService.recordFailedEvent(
                        providerCode,
                        providerEventId,
                        "transaction_callback",
                        externalOrderId,
                        payloadJson,
                        exception.getMessage()
                );
            } catch (Exception nestedException) {
                log.error("Billing bridge failed to persist failed provider event {}", providerEventId, nestedException);
            }
        }
    }

    private Company requireCompany(int companyId) {
        Company company = dbCompany.getCompanyById(companyId);
        if (company == null) {
            throw new IllegalStateException("Company not found for billing bridge: " + companyId);
        }
        return company;
    }

    private void syncLegacySnapshot(Company company,
                                    Branch branch,
                                    int legacySubscriptionId,
                                    Date startTime,
                                    Date endTime,
                                    BigDecimal amountToPay,
                                    BigDecimal amountPaid,
                                    String providerCode,
                                    int providerOrderId,
                                    String legacyStatus) {
        long billingAccountId = billingAccountService.ensureBillingAccount(company);
        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setBranchId(branch.getBranchID());
        request.setStartTime(startTime.toLocalDate());
        request.setEndTime(endTime.toLocalDate());
        request.setAmountToPay(amountToPay);
        request.setAmountPaid(amountPaid);

        long branchSubscriptionId = branchSubscriptionService.ensureLegacyMirroredSubscription(
                billingAccountId,
                company,
                branch,
                legacySubscriptionId,
                request
        );
        long invoiceId = invoiceService.ensureLegacyMirroredInvoice(
                billingAccountId,
                branchSubscriptionId,
                legacySubscriptionId,
                normalizeCurrency(company.getCurrency()),
                amountToPay,
                amountPaid,
                "Legacy branch subscription for " + branch.getBranchName()
        );

        if (providerOrderId > 0) {
            paymentAttemptService.ensureCreatedAttempt(
                    invoiceId,
                    providerCode,
                    String.valueOf(providerOrderId),
                    amountToPay,
                    normalizeCurrency(company.getCurrency()),
                    toJson(Map.of(
                            "legacySubscriptionId", legacySubscriptionId,
                            "branchId", branch.getBranchID(),
                            "companyId", company.getCompanyId()
                    )),
                    toJson(Map.of("providerOrderId", providerOrderId))
            );
        }

        if ("PD".equalsIgnoreCase(legacyStatus)) {
            if (providerOrderId > 0) {
                paymentAttemptService.markSucceeded(
                        providerCode,
                        String.valueOf(providerOrderId),
                        toJson(Map.of("legacyStatus", legacyStatus)),
                        String.valueOf(providerOrderId)
                );
                invoiceService.markPaidByExternalOrderId(providerCode, String.valueOf(providerOrderId));
                branchSubscriptionService.markPaidByExternalOrderId(providerCode, String.valueOf(providerOrderId));
            } else {
                invoiceService.markPaidByBranchSubscriptionId(branchSubscriptionId);
                branchSubscriptionService.markPaidByLegacySubscriptionId(legacySubscriptionId);
            }
            billingEntitlementService.recordActivated(
                    branch.getBranchID(),
                    providerOrderId > 0 ? String.valueOf(providerOrderId) : "legacy-sub-" + legacySubscriptionId
            );
            return;
        }

        billingEntitlementService.recordPendingPayment(branch.getBranchID(), branchSubscriptionId, invoiceId, legacySubscriptionId);
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "EGP" : currency.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
