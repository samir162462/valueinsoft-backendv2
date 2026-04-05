package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedWorkflowFlag;
import com.example.valueinsoftbackend.Model.Configuration.TenantAdminPortalConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertAcknowledgmentItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertAcknowledgmentsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertNotificationOutboxPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertSettingsResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompaniesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompany360Response;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanyBranchSummary;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanySubscriptionItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformConfigAssignmentsResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAuditEventsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSubscriptionsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSummaryResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformClientReceiptsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformExpensesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformLifecycleActionResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformMetricsRefreshResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformMetricsStatusResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformRevenueTrendResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupportNoteItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupportNotesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupplierReceiptsPageResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.CreatePlatformSupportNoteRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.PlatformAlertAcknowledgmentRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.PlatformLifecycleActionRequest;
import com.example.valueinsoftbackend.Service.PlatformAdminAlertService;
import com.example.valueinsoftbackend.Service.PlatformAdminAuditService;
import com.example.valueinsoftbackend.Service.PlatformAdminBillingService;
import com.example.valueinsoftbackend.Service.PlatformAdminCompanyService;
import com.example.valueinsoftbackend.Service.PlatformAdminConfigurationInspectorService;
import com.example.valueinsoftbackend.Service.PlatformAdminFinanceService;
import com.example.valueinsoftbackend.Service.PlatformAdminLifecycleService;
import com.example.valueinsoftbackend.Service.PlatformAdminMetricsService;
import com.example.valueinsoftbackend.Service.PlatformAdminOverviewService;
import com.example.valueinsoftbackend.Service.PlatformSupportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {

    private final PlatformAdminOverviewService platformAdminOverviewService;
    private final PlatformAdminAlertService platformAdminAlertService;
    private final PlatformAdminCompanyService platformAdminCompanyService;
    private final PlatformAdminConfigurationInspectorService platformAdminConfigurationInspectorService;
    private final PlatformAdminLifecycleService platformAdminLifecycleService;
    private final PlatformAdminAuditService platformAdminAuditService;
    private final PlatformAdminBillingService platformAdminBillingService;
    private final PlatformAdminFinanceService platformAdminFinanceService;
    private final PlatformAdminMetricsService platformAdminMetricsService;
    private final PlatformSupportService platformSupportService;

    public PlatformAdminController(PlatformAdminOverviewService platformAdminOverviewService,
                                   PlatformAdminAlertService platformAdminAlertService,
                                   PlatformAdminCompanyService platformAdminCompanyService,
                                   PlatformAdminConfigurationInspectorService platformAdminConfigurationInspectorService,
                                   PlatformAdminLifecycleService platformAdminLifecycleService,
                                   PlatformAdminAuditService platformAdminAuditService,
                                   PlatformAdminBillingService platformAdminBillingService,
                                   PlatformAdminFinanceService platformAdminFinanceService,
                                   PlatformAdminMetricsService platformAdminMetricsService,
                                   PlatformSupportService platformSupportService) {
        this.platformAdminOverviewService = platformAdminOverviewService;
        this.platformAdminAlertService = platformAdminAlertService;
        this.platformAdminCompanyService = platformAdminCompanyService;
        this.platformAdminConfigurationInspectorService = platformAdminConfigurationInspectorService;
        this.platformAdminLifecycleService = platformAdminLifecycleService;
        this.platformAdminAuditService = platformAdminAuditService;
        this.platformAdminBillingService = platformAdminBillingService;
        this.platformAdminFinanceService = platformAdminFinanceService;
        this.platformAdminMetricsService = platformAdminMetricsService;
        this.platformSupportService = platformSupportService;
    }

    @GetMapping("/overview")
    public PlatformOverviewResponse getOverview(Principal principal) {
        return platformAdminOverviewService.getOverviewForAuthenticatedUser(principal.getName());
    }

    @GetMapping("/overview/alerts/settings")
    public PlatformAlertSettingsResponse getAlertSettings(Principal principal) {
        return platformAdminAlertService.getAlertSettingsForAuthenticatedUser(principal.getName());
    }

    @GetMapping("/overview/alerts/acknowledgments")
    public PlatformAlertAcknowledgmentsPageResponse getAlertAcknowledgments(Principal principal,
                                                                            @RequestParam(value = "alertKey", required = false) String alertKey,
                                                                            @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                                            @RequestParam(value = "branchId", required = false) Integer branchId,
                                                                            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return platformAdminAlertService.getAcknowledgmentHistoryForAuthenticatedUser(
                principal.getName(),
                alertKey,
                tenantId,
                branchId,
                activeOnly,
                page,
                size
        );
    }

    @GetMapping("/overview/alerts/notifications/outbox")
    public PlatformAlertNotificationOutboxPageResponse getAlertNotificationOutbox(Principal principal,
                                                                                  @RequestParam(value = "alertKey", required = false) String alertKey,
                                                                                  @RequestParam(value = "eventType", required = false) String eventType,
                                                                                  @RequestParam(value = "status", required = false) String status,
                                                                                  @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                                                  @RequestParam(value = "branchId", required = false) Integer branchId,
                                                                                  @RequestParam(defaultValue = "1") int page,
                                                                                  @RequestParam(defaultValue = "20") int size) {
        return platformAdminAlertService.getNotificationOutboxForAuthenticatedUser(
                principal.getName(),
                alertKey,
                eventType,
                status,
                tenantId,
                branchId,
                page,
                size
        );
    }

    @PostMapping("/overview/alerts/{alertKey}/acknowledge")
    public PlatformAlertAcknowledgmentItem acknowledgeAlert(Principal principal,
                                                            @PathVariable String alertKey,
                                                            @Valid @RequestBody(required = false) PlatformAlertAcknowledgmentRequest request) {
        return platformAdminAlertService.acknowledgeAlertForAuthenticatedUser(
                principal.getName(),
                alertKey,
                request
        );
    }

    @DeleteMapping("/overview/alerts/{alertKey}/acknowledgment")
    public PlatformAlertAcknowledgmentItem clearAlertAcknowledgment(Principal principal,
                                                                    @PathVariable String alertKey,
                                                                    @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                                    @RequestParam(value = "branchId", required = false) Integer branchId,
                                                                    @RequestParam(value = "notify", defaultValue = "false") boolean notify) {
        return platformAdminAlertService.clearAcknowledgmentForAuthenticatedUser(
                principal.getName(),
                alertKey,
                tenantId,
                branchId,
                notify
        );
    }

    @GetMapping("/companies")
    public PlatformCompaniesPageResponse getCompanies(Principal principal,
                                                      @RequestParam(required = false) String search,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String packageId,
                                                      @RequestParam(required = false) String templateId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return platformAdminCompanyService.getCompaniesForAuthenticatedUser(
                principal.getName(),
                search,
                status,
                packageId,
                templateId,
                page,
                size
        );
    }

    @GetMapping("/companies/{tenantId}")
    public PlatformCompany360Response getCompany360(Principal principal,
                                                    @PathVariable int tenantId) {
        return platformAdminCompanyService.getCompany360ForAuthenticatedUser(principal.getName(), tenantId);
    }

    @GetMapping("/companies/{tenantId}/branches")
    public ArrayList<PlatformCompanyBranchSummary> getCompanyBranches(Principal principal,
                                                                      @PathVariable int tenantId) {
        return platformAdminCompanyService.getCompanyBranchesForAuthenticatedUser(principal.getName(), tenantId);
    }

    @GetMapping("/companies/{tenantId}/users")
    public ArrayList<ConfigurationAdminUserSummary> getCompanyUsers(Principal principal,
                                                                    @PathVariable int tenantId,
                                                                    @RequestParam(value = "branchId", required = false) Integer branchId) {
        return platformAdminCompanyService.getCompanyUsersForAuthenticatedUser(principal.getName(), tenantId, branchId);
    }

    @GetMapping("/companies/{tenantId}/subscriptions")
    public ArrayList<PlatformCompanySubscriptionItem> getCompanySubscriptions(Principal principal,
                                                                              @PathVariable int tenantId) {
        return platformAdminCompanyService.getCompanySubscriptionsForAuthenticatedUser(principal.getName(), tenantId);
    }

    @GetMapping("/companies/{tenantId}/clients")
    public ArrayList<Client> getCompanyClients(Principal principal,
                                               @PathVariable int tenantId,
                                               @RequestParam(value = "branchId", required = false) Integer branchId,
                                               @RequestParam(value = "max", required = false) Integer max) {
        return platformAdminCompanyService.getCompanyClientsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                max
        );
    }

    @GetMapping("/companies/{tenantId}/products")
    public ArrayList<Product> getCompanyProducts(Principal principal,
                                                 @PathVariable int tenantId,
                                                 @RequestParam(value = "branchId", required = false) Integer branchId,
                                                 @RequestParam(value = "max", required = false) Integer max) {
        return platformAdminCompanyService.getCompanyProductsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                max
        );
    }

    @GetMapping("/billing/summary")
    public PlatformBillingSummaryResponse getBillingSummary(Principal principal,
                                                            @RequestParam(value = "packageId", required = false) String packageId) {
        return platformAdminBillingService.getBillingSummaryForAuthenticatedUser(principal.getName(), packageId);
    }

    @GetMapping("/billing/subscriptions")
    public PlatformBillingSubscriptionsPageResponse getBillingSubscriptions(Principal principal,
                                                                            @RequestParam(value = "search", required = false) String search,
                                                                            @RequestParam(value = "status", required = false) String status,
                                                                            @RequestParam(value = "packageId", required = false) String packageId,
                                                                            @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return platformAdminBillingService.getLatestSubscriptionsForAuthenticatedUser(
                principal.getName(),
                search,
                status,
                packageId,
                tenantId,
                page,
                size
        );
    }

    @GetMapping("/billing/revenue-trend")
    public PlatformRevenueTrendResponse getRevenueTrend(Principal principal,
                                                        @RequestParam(value = "days", defaultValue = "30") int days,
                                                        @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                        @RequestParam(value = "packageId", required = false) String packageId) {
        return platformAdminMetricsService.getRevenueTrendForAuthenticatedUser(
                principal.getName(),
                days,
                tenantId,
                packageId
        );
    }

    @PostMapping("/metrics/daily/refresh")
    public PlatformMetricsRefreshResponse refreshDailyMetrics(Principal principal,
                                                              @RequestParam(value = "metricDate", required = false)
                                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate metricDate,
                                                              @RequestParam(value = "tenantId", required = false) Integer tenantId) {
        return platformAdminMetricsService.refreshDailyMetricsForAuthenticatedUser(
                principal.getName(),
                metricDate,
                tenantId
        );
    }

    @GetMapping("/metrics/daily/status")
    public PlatformMetricsStatusResponse getDailyMetricsStatus(Principal principal) {
        return platformAdminMetricsService.getMetricsStatusForAuthenticatedUser(principal.getName());
    }

    @GetMapping("/companies/{tenantId}/finance/expenses")
    public PlatformExpensesPageResponse getCompanyExpenses(Principal principal,
                                                           @PathVariable int tenantId,
                                                           @RequestParam(value = "branchId", required = false) Integer branchId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return platformAdminFinanceService.getCompanyExpensesForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                page,
                size
        );
    }

    @GetMapping("/companies/{tenantId}/finance/client-receipts")
    public PlatformClientReceiptsPageResponse getCompanyClientReceipts(Principal principal,
                                                                       @PathVariable int tenantId,
                                                                       @RequestParam(value = "branchId", required = false) Integer branchId,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return platformAdminFinanceService.getCompanyClientReceiptsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                page,
                size
        );
    }

    @GetMapping("/companies/{tenantId}/finance/supplier-receipts")
    public PlatformSupplierReceiptsPageResponse getCompanySupplierReceipts(Principal principal,
                                                                           @PathVariable int tenantId,
                                                                           @RequestParam(value = "branchId", required = false) Integer branchId,
                                                                           @RequestParam(defaultValue = "1") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return platformAdminFinanceService.getCompanySupplierReceiptsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                page,
                size
        );
    }

    @GetMapping("/companies/{tenantId}/config/effective")
    public TenantAdminPortalConfig getCompanyConfigurationEffective(Principal principal,
                                                                    @PathVariable int tenantId,
                                                                    @RequestParam(value = "branchId", required = false) Integer branchId) {
        return platformAdminConfigurationInspectorService.getConfigurationPortalForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    @GetMapping("/companies/{tenantId}/config/modules")
    public ArrayList<EffectiveModuleConfig> getCompanyConfigurationModules(Principal principal,
                                                                           @PathVariable int tenantId,
                                                                           @RequestParam(value = "branchId", required = false) Integer branchId) {
        return platformAdminConfigurationInspectorService.getModulesForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    @GetMapping("/companies/{tenantId}/config/workflows")
    public ArrayList<ResolvedWorkflowFlag> getCompanyConfigurationWorkflows(Principal principal,
                                                                            @PathVariable int tenantId,
                                                                            @RequestParam(value = "branchId", required = false) Integer branchId) {
        return platformAdminConfigurationInspectorService.getWorkflowFlagsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    @GetMapping("/companies/{tenantId}/config/assignments")
    public PlatformConfigAssignmentsResponse getCompanyConfigurationAssignments(Principal principal,
                                                                               @PathVariable int tenantId,
                                                                               @RequestParam(value = "branchId", required = false) Integer branchId) {
        return platformAdminConfigurationInspectorService.getAssignmentsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    @GetMapping("/companies/{tenantId}/config/user-overrides")
    public ArrayList<TenantUserGrantOverrideConfig> getCompanyConfigurationUserOverrides(Principal principal,
                                                                                          @PathVariable int tenantId,
                                                                                          @RequestParam(value = "branchId", required = false) Integer branchId) {
        return platformAdminConfigurationInspectorService.getUserOverridesForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    @GetMapping("/audit/events")
    public PlatformAuditEventsPageResponse getAuditEvents(Principal principal,
                                                          @RequestParam(value = "targetTenantId", required = false) Integer targetTenantId,
                                                          @RequestParam(value = "targetBranchId", required = false) Integer targetBranchId,
                                                          @RequestParam(value = "actorUserName", required = false) String actorUserName,
                                                          @RequestParam(value = "actionType", required = false) String actionType,
                                                          @RequestParam(value = "resultStatus", required = false) String resultStatus,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return platformAdminAuditService.getAuditEventsForAuthenticatedUser(
                principal.getName(),
                targetTenantId,
                targetBranchId,
                actorUserName,
                actionType,
                resultStatus,
                page,
                size
        );
    }

    @GetMapping("/companies/{tenantId}/audit/events")
    public PlatformAuditEventsPageResponse getCompanyAuditEvents(Principal principal,
                                                                 @PathVariable int tenantId,
                                                                 @RequestParam(value = "targetBranchId", required = false) Integer targetBranchId,
                                                                 @RequestParam(value = "actorUserName", required = false) String actorUserName,
                                                                 @RequestParam(value = "actionType", required = false) String actionType,
                                                                 @RequestParam(value = "resultStatus", required = false) String resultStatus,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return platformAdminAuditService.getAuditEventsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                targetBranchId,
                actorUserName,
                actionType,
                resultStatus,
                page,
                size
        );
    }

    @GetMapping("/support/notes")
    public PlatformSupportNotesPageResponse getSupportNotes(Principal principal,
                                                            @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                            @RequestParam(value = "branchId", required = false) Integer branchId,
                                                            @RequestParam(value = "noteType", required = false) String noteType,
                                                            @RequestParam(value = "visibility", required = false) String visibility,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return platformSupportService.getNotesForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                noteType,
                visibility,
                page,
                size
        );
    }

    @GetMapping("/companies/{tenantId}/support/notes")
    public PlatformSupportNotesPageResponse getCompanySupportNotes(Principal principal,
                                                                   @PathVariable int tenantId,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return platformSupportService.getTenantNotesForAuthenticatedUser(
                principal.getName(),
                tenantId,
                page,
                size
        );
    }

    @PostMapping("/support/notes")
    public PlatformSupportNoteItem createSupportNote(Principal principal,
                                                     @Valid @RequestBody CreatePlatformSupportNoteRequest request) {
        return platformSupportService.createNoteForAuthenticatedUser(principal.getName(), request);
    }

    @PostMapping("/companies/{tenantId}/suspend")
    public PlatformLifecycleActionResponse suspendCompany(Principal principal,
                                                          @PathVariable int tenantId,
                                                          @Valid @RequestBody PlatformLifecycleActionRequest request) {
        return platformAdminLifecycleService.suspendCompanyForAuthenticatedUser(
                principal.getName(),
                tenantId,
                request
        );
    }

    @PostMapping("/companies/{tenantId}/resume")
    public PlatformLifecycleActionResponse resumeCompany(Principal principal,
                                                         @PathVariable int tenantId,
                                                         @Valid @RequestBody PlatformLifecycleActionRequest request) {
        return platformAdminLifecycleService.resumeCompanyForAuthenticatedUser(
                principal.getName(),
                tenantId,
                request
        );
    }

    @PostMapping("/branches/{branchId}/lock")
    public PlatformLifecycleActionResponse lockBranch(Principal principal,
                                                      @PathVariable int branchId,
                                                      @Valid @RequestBody PlatformLifecycleActionRequest request) {
        return platformAdminLifecycleService.lockBranchForAuthenticatedUser(
                principal.getName(),
                branchId,
                request
        );
    }

    @PostMapping("/branches/{branchId}/unlock")
    public PlatformLifecycleActionResponse unlockBranch(Principal principal,
                                                        @PathVariable int branchId,
                                                        @Valid @RequestBody PlatformLifecycleActionRequest request) {
        return platformAdminLifecycleService.unlockBranchForAuthenticatedUser(
                principal.getName(),
                branchId,
                request
        );
    }
}
