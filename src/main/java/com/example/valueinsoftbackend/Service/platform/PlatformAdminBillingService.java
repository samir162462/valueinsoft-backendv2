package com.example.valueinsoftbackend.Service.platform;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingAdminReadModels;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingDunningRunsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingEntitlementsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingHealthSnapshotResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingInvoicesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingManualActionResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingOperationResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingPaymentAttemptsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingPaymentsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingProviderEventsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingReconciliationPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingRenewalBacklogPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingRetryInvoiceResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSubscriptionsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSummaryResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.ManualBillingActionRequest;
import com.example.valueinsoftbackend.Service.ManualBillingAdjustmentService;
import com.example.valueinsoftbackend.Service.billing.BillingSchedulerService;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminBillingService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final DbBillingAdminReadModels dbBillingAdminReadModels;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final BillingSchedulerService billingSchedulerService;
    private final ManualBillingAdjustmentService manualBillingAdjustmentService;

    public PlatformAdminBillingService(DbBillingAdminReadModels dbBillingAdminReadModels,
                                       PlatformAuthorizationService platformAuthorizationService,
                                       BillingSchedulerService billingSchedulerService,
                                       ManualBillingAdjustmentService manualBillingAdjustmentService) {
        this.dbBillingAdminReadModels = dbBillingAdminReadModels;
        this.platformAuthorizationService = platformAuthorizationService;
        this.billingSchedulerService = billingSchedulerService;
        this.manualBillingAdjustmentService = manualBillingAdjustmentService;
    }

    public PlatformBillingSummaryResponse getBillingSummaryForAuthenticatedUser(String authenticatedName, String packageId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbBillingAdminReadModels.getModernBillingSummary(packageId);
    }

    public PlatformBillingSubscriptionsPageResponse getLatestSubscriptionsForAuthenticatedUser(String authenticatedName,
                                                                                               String search,
                                                                                               String status,
                                                                                               String packageId,
                                                                                               Integer tenantId,
                                                                                               int page,
                                                                                               int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbBillingAdminReadModels.getLatestModernSubscriptions(
                search,
                status,
                packageId,
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingInvoicesPageResponse getInvoicesForAuthenticatedUser(String authenticatedName,
                                                                               String search,
                                                                               String status,
                                                                               String providerCode,
                                                                               Integer tenantId,
                                                                               int page,
                                                                               int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbBillingAdminReadModels.getInvoices(
                search,
                status,
                providerCode,
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingPaymentAttemptsPageResponse getPaymentAttemptsForAuthenticatedUser(String authenticatedName,
                                                                                             String search,
                                                                                             String status,
                                                                                             String providerCode,
                                                                                             Integer tenantId,
                                                                                             int page,
                                                                                             int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbBillingAdminReadModels.getPaymentAttempts(
                search,
                status,
                providerCode,
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingProviderEventsPageResponse getProviderEventsForAuthenticatedUser(String authenticatedName,
                                                                                           String search,
                                                                                           String processingStatus,
                                                                                           String providerCode,
                                                                                           Integer tenantId,
                                                                                           Long billingInvoiceId,
                                                                                           int page,
                                                                                           int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbBillingAdminReadModels.getProviderEvents(
                search,
                processingStatus,
                providerCode,
                tenantId,
                billingInvoiceId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingPaymentsPageResponse getPaymentsForAuthenticatedUser(String authenticatedName,
                                                                               String search,
                                                                               String status,
                                                                               String paymentSource,
                                                                               Integer tenantId,
                                                                               int page,
                                                                               int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbBillingAdminReadModels.getPayments(
                search,
                status,
                paymentSource,
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingDunningRunsPageResponse getDunningRunsForAuthenticatedUser(String authenticatedName,
                                                                                     String status,
                                                                                     Integer tenantId,
                                                                                     int page,
                                                                                     int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return billingSchedulerService.getDunningRuns(
                status,
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingRenewalBacklogPageResponse getRenewalBacklogForAuthenticatedUser(String authenticatedName,
                                                                                           Integer tenantId,
                                                                                           int page,
                                                                                           int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return billingSchedulerService.getRenewalBacklog(
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingEntitlementsPageResponse getEntitlementsForAuthenticatedUser(String authenticatedName,
                                                                                       Integer tenantId,
                                                                                       Integer branchId,
                                                                                       String currentState,
                                                                                       int page,
                                                                                       int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return billingSchedulerService.getEntitlements(
                tenantId,
                branchId,
                currentState,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingHealthSnapshotResponse getBillingHealthSnapshotForAuthenticatedUser(String authenticatedName,
                                                                                              Integer tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return billingSchedulerService.getBillingHealthSnapshot(tenantId);
    }

    public PlatformBillingReconciliationPageResponse getReconciliationForAuthenticatedUser(String authenticatedName,
                                                                                           String reconciliationStatus,
                                                                                           Integer tenantId,
                                                                                           int page,
                                                                                           int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbBillingAdminReadModels.getReconciliation(
                reconciliationStatus,
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformBillingOperationResponse runRenewalCycleForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        return billingSchedulerService.runRenewalCycle(authenticatedName);
    }

    public PlatformBillingOperationResponse runDunningCycleForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        return billingSchedulerService.runDunningCycle(authenticatedName);
    }

    public PlatformBillingRetryInvoiceResponse retryInvoiceForAuthenticatedUser(String authenticatedName,
                                                                                long billingInvoiceId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        return billingSchedulerService.retryInvoice(billingInvoiceId, authenticatedName);
    }

    public PlatformBillingManualActionResponse recordManualPaymentForAuthenticatedUser(String authenticatedName,
                                                                                      long billingInvoiceId,
                                                                                      ManualBillingActionRequest request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        return manualBillingAdjustmentService.recordManualPayment(billingInvoiceId, request, authenticatedName);
    }

    public PlatformBillingManualActionResponse markInvoiceUnpaidForAuthenticatedUser(String authenticatedName,
                                                                                    long billingInvoiceId,
                                                                                    ManualBillingActionRequest request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        return manualBillingAdjustmentService.markInvoiceUnpaid(billingInvoiceId, request, authenticatedName);
    }

    public PlatformBillingManualActionResponse refundInvoiceForAuthenticatedUser(String authenticatedName,
                                                                                long billingInvoiceId,
                                                                                ManualBillingActionRequest request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        return manualBillingAdjustmentService.refundInvoice(billingInvoiceId, request, authenticatedName);
    }

    private int sanitizePage(int page) {
        return Math.max(1, page);
    }

    private int sanitizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

}
