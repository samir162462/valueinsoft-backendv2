package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.DatabaseRequests.DbBillingAdminReadModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAuditWriter;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoiceRetryCandidate;
import com.example.valueinsoftbackend.Model.Billing.BillingOverdueInvoiceCandidate;
import com.example.valueinsoftbackend.Model.Billing.BillingRenewalCandidate;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingDunningRunsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingEntitlementsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingHealthSnapshotResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingOperationResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingRenewalBacklogPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingRetryInvoiceResponse;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillingSchedulerService {

    private final DbBillingWriteModels dbBillingWriteModels;
    private final DbBillingAdminReadModels dbBillingAdminReadModels;
    private final DbPlatformAdminAuditWriter dbPlatformAdminAuditWriter;
    private final InvoiceService invoiceService;
    private final PaymentAttemptService paymentAttemptService;
    private final BillingEntitlementService billingEntitlementService;
    private final PaymentProviderResolver paymentProviderResolver;
    private final BillingProperties billingProperties;

    public BillingSchedulerService(DbBillingWriteModels dbBillingWriteModels,
                                   DbBillingAdminReadModels dbBillingAdminReadModels,
                                   DbPlatformAdminAuditWriter dbPlatformAdminAuditWriter,
                                   InvoiceService invoiceService,
                                   PaymentAttemptService paymentAttemptService,
                                   BillingEntitlementService billingEntitlementService,
                                   PaymentProviderResolver paymentProviderResolver,
                                   BillingProperties billingProperties) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.dbBillingAdminReadModels = dbBillingAdminReadModels;
        this.dbPlatformAdminAuditWriter = dbPlatformAdminAuditWriter;
        this.invoiceService = invoiceService;
        this.paymentAttemptService = paymentAttemptService;
        this.billingEntitlementService = billingEntitlementService;
        this.paymentProviderResolver = paymentProviderResolver;
        this.billingProperties = billingProperties;
    }

    @Transactional
    public PlatformBillingOperationResponse runRenewalCycle() {
        return runRenewalCycle(null);
    }

    @Transactional
    public PlatformBillingOperationResponse runRenewalCycle(String actorUserName) {
        LocalDate today = LocalDate.now();
        try {
            List<BillingRenewalCandidate> candidates = dbBillingWriteModels.findRenewalCandidates(
                    today,
                    Math.max(0, billingProperties.getRenewalLeadDays())
            );

            int generated = 0;
            int skipped = 0;
            for (BillingRenewalCandidate candidate : candidates) {
                if (processRenewalCandidate(candidate)) {
                    generated++;
                } else {
                    skipped++;
                }
            }

            PlatformBillingOperationResponse response = new PlatformBillingOperationResponse(
                    "renewal_cycle",
                    candidates.size(),
                    generated,
                    skipped,
                    new Timestamp(System.currentTimeMillis())
            );
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.renewals.run",
                    "{\"renewalLeadDays\":" + Math.max(0, billingProperties.getRenewalLeadDays()) + "}",
                    "{\"processedItems\":" + response.getProcessedItems() + ",\"generatedItems\":" + response.getGeneratedItems() + ",\"skippedItems\":" + response.getSkippedItems() + "}",
                    "success",
                    null,
                    null
            );
            return response;
        } catch (RuntimeException ex) {
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.renewals.run",
                    "{\"renewalLeadDays\":" + Math.max(0, billingProperties.getRenewalLeadDays()) + "}",
                    "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}",
                    "failure",
                    null,
                    null
            );
            throw ex;
        }
    }

    @Transactional
    public PlatformBillingOperationResponse runDunningCycle() {
        return runDunningCycle(null);
    }

    @Transactional
    public PlatformBillingOperationResponse runDunningCycle(String actorUserName) {
        LocalDate today = LocalDate.now();
        try {
            List<BillingOverdueInvoiceCandidate> candidates = dbBillingWriteModels.findOverdueInvoices(
                    today,
                    Math.max(0, billingProperties.getDunningGraceDays()),
                    Math.max(1, billingProperties.getDunningMaxAttempts())
            );

            int generated = 0;
            int skipped = 0;
            for (BillingOverdueInvoiceCandidate candidate : candidates) {
                if (processDunningCandidate(candidate)) {
                    generated++;
                } else {
                    skipped++;
                }
            }

            PlatformBillingOperationResponse response = new PlatformBillingOperationResponse(
                    "dunning_cycle",
                    candidates.size(),
                    generated,
                    skipped,
                    new Timestamp(System.currentTimeMillis())
            );
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.dunning.run",
                    "{\"dunningGraceDays\":" + Math.max(0, billingProperties.getDunningGraceDays()) + ",\"dunningMaxAttempts\":" + Math.max(1, billingProperties.getDunningMaxAttempts()) + "}",
                    "{\"processedItems\":" + response.getProcessedItems() + ",\"generatedItems\":" + response.getGeneratedItems() + ",\"skippedItems\":" + response.getSkippedItems() + "}",
                    "success",
                    null,
                    null
            );
            return response;
        } catch (RuntimeException ex) {
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.dunning.run",
                    "{\"dunningGraceDays\":" + Math.max(0, billingProperties.getDunningGraceDays()) + ",\"dunningMaxAttempts\":" + Math.max(1, billingProperties.getDunningMaxAttempts()) + "}",
                    "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}",
                    "failure",
                    null,
                    null
            );
            throw ex;
        }
    }

    public PlatformBillingDunningRunsPageResponse getDunningRuns(String status,
                                                                 Integer tenantId,
                                                                 int page,
                                                                 int size) {
        return dbBillingAdminReadModels.getDunningRuns(status, tenantId, sanitizePage(page), sanitizeSize(size));
    }

    public PlatformBillingRenewalBacklogPageResponse getRenewalBacklog(Integer tenantId,
                                                                       int page,
                                                                       int size) {
        return dbBillingAdminReadModels.getRenewalBacklog(
                tenantId,
                Math.max(0, billingProperties.getRenewalLeadDays()),
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingEntitlementsPageResponse getEntitlements(Integer tenantId,
                                                                   Integer branchId,
                                                                   String currentState,
                                                                   int page,
                                                                   int size) {
        return dbBillingAdminReadModels.getEntitlements(
                tenantId,
                branchId,
                currentState,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingHealthSnapshotResponse getBillingHealthSnapshot(Integer tenantId) {
        return dbBillingAdminReadModels.getBillingHealthSnapshot(
                tenantId,
                Math.max(0, billingProperties.getRenewalLeadDays()),
                Math.max(0, billingProperties.getDunningGraceDays()),
                Math.max(0, billingProperties.getManualRetryCooldownMinutes()),
                Math.max(1, billingProperties.getManualRetryMaxAttempts())
        );
    }

    @Transactional
    public PlatformBillingRetryInvoiceResponse retryInvoice(long billingInvoiceId) {
        return retryInvoice(billingInvoiceId, null);
    }

    @Transactional
    public PlatformBillingRetryInvoiceResponse retryInvoice(long billingInvoiceId, String actorUserName) {
        BillingInvoiceRetryCandidate candidate = dbBillingWriteModels.findInvoiceRetryCandidate(billingInvoiceId);
        if (candidate == null) {
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.invoice.retry",
                    "{\"billingInvoiceId\":" + billingInvoiceId + "}",
                    "{\"error\":\"Billing invoice not found\"}",
                    "failure",
                    null,
                    null
            );
            throw new ApiException(HttpStatus.NOT_FOUND, "BILLING_INVOICE_NOT_FOUND", "Billing invoice not found");
        }
        if (!"open".equalsIgnoreCase(candidate.getInvoiceStatus())) {
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.invoice.retry",
                    "{\"billingInvoiceId\":" + billingInvoiceId + "}",
                    "{\"error\":\"Only open invoices can be retried\"}",
                    "failure",
                    candidate.getTenantId(),
                    candidate.getBranchId()
            );
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_INVOICE_NOT_OPEN", "Only open invoices can be retried");
        }
        if (candidate.getDueAmount() == null || candidate.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.invoice.retry",
                    "{\"billingInvoiceId\":" + billingInvoiceId + "}",
                    "{\"error\":\"Invoice does not have due amount to retry\"}",
                    "failure",
                    candidate.getTenantId(),
                    candidate.getBranchId()
            );
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_INVOICE_NOT_DUE", "Invoice does not have due amount to retry");
        }
        if (candidate.getAttemptCount() >= Math.max(1, billingProperties.getManualRetryMaxAttempts())) {
            auditBillingOperation(
                    actorUserName,
                    "platform.billing.invoice.retry",
                    "{\"billingInvoiceId\":" + billingInvoiceId + "}",
                    "{\"error\":\"Manual retry max attempts exceeded\",\"attemptCount\":" + candidate.getAttemptCount() + "}",
                    "failure",
                    candidate.getTenantId(),
                    candidate.getBranchId()
            );
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_RETRY_LIMIT_REACHED", "Manual retry limit reached for this invoice");
        }
        if (candidate.getLatestAttemptAt() != null) {
            Timestamp retryAllowedAt = Timestamp.valueOf(
                    candidate.getLatestAttemptAt().toLocalDateTime().plusMinutes(Math.max(0, billingProperties.getManualRetryCooldownMinutes()))
            );
            if (retryAllowedAt.after(Timestamp.valueOf(LocalDateTime.now()))) {
                auditBillingOperation(
                        actorUserName,
                        "platform.billing.invoice.retry",
                        "{\"billingInvoiceId\":" + billingInvoiceId + "}",
                        "{\"error\":\"Manual retry cooldown is active\",\"retryAllowedAt\":\"" + retryAllowedAt + "\"}",
                        "failure",
                        candidate.getTenantId(),
                        candidate.getBranchId()
                );
                throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_RETRY_COOLDOWN_ACTIVE", "Manual retry cooldown is still active for this invoice");
            }
        }

        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        int providerOrderId = paymentProvider.createProviderOrder(
                Math.toIntExact(candidate.getBranchSubscriptionId()),
                candidate.getBranchId(),
                candidate.getDueAmount()
        );

        long paymentAttemptId = paymentAttemptService.ensureCreatedAttempt(
                candidate.getBillingInvoiceId(),
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                candidate.getDueAmount(),
                normalizeCurrency(candidate.getCurrencyCode()),
                "{\"source\":\"platform_invoice_retry\",\"billingInvoiceId\":" + candidate.getBillingInvoiceId() + "}",
                "{\"providerOrderId\":" + providerOrderId + "}"
        );

        PaymentTokenRequest request = new PaymentTokenRequest();
        request.setOrderId(providerOrderId);
        request.setBranchId(candidate.getBranchId());
        request.setCompanyId(candidate.getCompanyId());
        request.setCurrency(normalizeCurrency(candidate.getCurrencyCode()));
        request.setAmountCents(candidate.getDueAmount().multiply(BigDecimal.valueOf(100L)).longValue());
        String checkoutUrl = paymentProvider.createPaymentKeyUrl(request);
        paymentAttemptService.markCheckoutRequested(
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                "{\"checkoutUrl\":\"" + checkoutUrl + "\",\"source\":\"platform_invoice_retry\"}"
        );

        PlatformBillingRetryInvoiceResponse response = new PlatformBillingRetryInvoiceResponse(
                candidate.getBillingInvoiceId(),
                paymentAttemptId,
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                checkoutUrl,
                candidate.getDueAmount(),
                normalizeCurrency(candidate.getCurrencyCode()),
                new Timestamp(System.currentTimeMillis())
        );
        auditBillingOperation(
                actorUserName,
                "platform.billing.invoice.retry",
                "{\"billingInvoiceId\":" + billingInvoiceId + ",\"attemptCountBeforeRetry\":" + candidate.getAttemptCount() + "}",
                "{\"billingPaymentAttemptId\":" + paymentAttemptId + ",\"providerCode\":\"" + escapeJson(paymentProvider.getProviderCode()) + "\",\"externalOrderId\":\"" + escapeJson(String.valueOf(providerOrderId)) + "\"}",
                "success",
                candidate.getTenantId(),
                candidate.getBranchId()
        );
        return response;
    }

    private boolean processRenewalCandidate(BillingRenewalCandidate candidate) {
        Date nextPeriodStart = Date.valueOf(candidate.getCurrentPeriodEnd().toLocalDate().plusDays(1));
        Date nextPeriodEnd = calculateNextPeriodEnd(nextPeriodStart, candidate.getBillingInterval());
        if (dbBillingWriteModels.branchSubscriptionExistsForPeriod(candidate.getBranchId(), nextPeriodStart, nextPeriodEnd)) {
            return false;
        }

        long nextBranchSubscriptionId = dbBillingWriteModels.createBranchSubscription(
                candidate.getBillingAccountId(),
                candidate.getBranchId(),
                candidate.getTenantId(),
                0,
                candidate.getPriceCode(),
                "pending_payment",
                candidate.getUnitAmount(),
                nextPeriodStart,
                nextPeriodStart,
                nextPeriodEnd,
                "{\"source\":\"billing_scheduler_renewal\",\"previousBranchSubscriptionId\":" + candidate.getPreviousBranchSubscriptionId() + "}"
        );

        long invoiceId = invoiceService.ensureBranchSubscriptionInvoice(
                candidate.getBillingAccountId(),
                nextBranchSubscriptionId,
                normalizeCurrency(candidate.getCurrencyCode()),
                candidate.getUnitAmount(),
                BigDecimal.ZERO,
                "Renewal subscription for " + candidate.getBranchName()
        );

        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        int providerOrderId = paymentProvider.createProviderOrder(
                Math.toIntExact(nextBranchSubscriptionId),
                candidate.getBranchId(),
                candidate.getUnitAmount()
        );

        paymentAttemptService.ensureCreatedAttempt(
                invoiceId,
                paymentProvider.getProviderCode(),
                String.valueOf(providerOrderId),
                candidate.getUnitAmount(),
                normalizeCurrency(candidate.getCurrencyCode()),
                "{\"source\":\"billing_scheduler_renewal\",\"branchSubscriptionId\":" + nextBranchSubscriptionId + ",\"branchId\":" + candidate.getBranchId() + "}",
                "{\"providerOrderId\":" + providerOrderId + "}"
        );

        billingEntitlementService.recordPendingRenewal(
                candidate.getBranchId(),
                nextBranchSubscriptionId,
                invoiceId,
                candidate.getPreviousBranchSubscriptionId()
        );
        return true;
    }

    private boolean processDunningCandidate(BillingOverdueInvoiceCandidate candidate) {
        int nextAttemptNumber = candidate.getExistingDunningAttempts() + 1;
        long dunningRunId = dbBillingWriteModels.createDunningRun(
                candidate.getBillingAccountId(),
                candidate.getBillingInvoiceId(),
                "queued",
                nextAttemptNumber,
                new Timestamp(System.currentTimeMillis()),
                "{\"source\":\"billing_scheduler_dunning\",\"branchSubscriptionId\":" + candidate.getBranchSubscriptionId() + "}"
        );

        dbBillingWriteModels.completeDunningRun(
                dunningRunId,
                "executed",
                "Queued manual follow-up for overdue invoice " + candidate.getBillingInvoiceId()
        );
        billingEntitlementService.recordPastDue(
                candidate.getBranchId(),
                candidate.getBranchSubscriptionId(),
                candidate.getBillingInvoiceId(),
                nextAttemptNumber
        );
        return true;
    }

    private Date calculateNextPeriodEnd(Date periodStart, String billingInterval) {
        LocalDate start = periodStart.toLocalDate();
        if ("yearly".equalsIgnoreCase(billingInterval)) {
            return Date.valueOf(start.plusYears(1).minusDays(1));
        }
        return Date.valueOf(start.plusMonths(1).minusDays(1));
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode.trim();
    }

    private void auditBillingOperation(String actorUserName,
                                       String actionType,
                                       String requestSummaryJson,
                                       String contextSummaryJson,
                                       String resultStatus,
                                       Integer targetTenantId,
                                       Integer targetBranchId) {
        if (actorUserName == null || actorUserName.trim().isEmpty()) {
            return;
        }
        dbPlatformAdminAuditWriter.createAuditEvent(
                actorUserName,
                "platform.admin.write",
                actionType,
                targetTenantId,
                targetBranchId,
                requestSummaryJson,
                contextSummaryJson,
                resultStatus
        );
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int sanitizePage(int page) {
        return Math.max(1, page);
    }

    private int sanitizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 200);
    }
}
