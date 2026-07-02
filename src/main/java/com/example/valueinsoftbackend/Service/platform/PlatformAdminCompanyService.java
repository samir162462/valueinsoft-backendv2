package com.example.valueinsoftbackend.Service.platform;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbClient;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbConfigurationAdmin;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingHealthSnapshotResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformTenantBillingWorkflowSummaryResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanyBranchSummary;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanyBranchSubscriptionItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAuditEventItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupportNoteItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminReadModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompaniesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompany360Response;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.billing.BillingSchedulerService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PlatformAdminCompanyService {

    private static final int DEFAULT_DETAIL_LIMIT = 200;
    private static final int MAX_DETAIL_LIMIT = 1000;
    private static final ProductFilter DEFAULT_PRODUCT_FILTER =
            new ProductFilter(false, false, false, false, 0, 100000, null, null);

    private final DbPlatformAdminReadModels dbPlatformAdminReadModels;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final DbConfigurationAdmin dbConfigurationAdmin;
    private final DbClient dbClient;
    private final DbPosProduct dbPosProduct;
    private final DbUsers dbUsers;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final BillingSchedulerService billingSchedulerService;
    private final PlatformAdminAuditService platformAdminAuditService;
    private final PlatformSupportService platformSupportService;

    public PlatformAdminCompanyService(DbPlatformAdminReadModels dbPlatformAdminReadModels,
                                       DbCompany dbCompany,
                                       DbBranch dbBranch,
                                       DbConfigurationAdmin dbConfigurationAdmin,
                                       DbClient dbClient,
                                       DbPosProduct dbPosProduct,
                                       DbUsers dbUsers,
                                       PlatformAuthorizationService platformAuthorizationService,
                                       BillingSchedulerService billingSchedulerService,
                                       PlatformAdminAuditService platformAdminAuditService,
                                       PlatformSupportService platformSupportService) {
        this.dbPlatformAdminReadModels = dbPlatformAdminReadModels;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.dbConfigurationAdmin = dbConfigurationAdmin;
        this.dbClient = dbClient;
        this.dbPosProduct = dbPosProduct;
        this.dbUsers = dbUsers;
        this.platformAuthorizationService = platformAuthorizationService;
        this.billingSchedulerService = billingSchedulerService;
        this.platformAdminAuditService = platformAdminAuditService;
        this.platformSupportService = platformSupportService;
    }

    public PlatformCompaniesPageResponse getCompaniesForAuthenticatedUser(String authenticatedName,
                                                                          String search,
                                                                          String status,
                                                                          String packageId,
                                                                          String templateId,
                                                                          int page,
                                                                          int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        return dbPlatformAdminReadModels.getCompanies(search, status, packageId, templateId, page, size);
    }

    public PlatformCompany360Response getCompany360ForAuthenticatedUser(String authenticatedName, int tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        requireCompany(tenantId);
        PlatformCompany360Response response = dbPlatformAdminReadModels.getCompany360(tenantId);
        PlatformBillingHealthSnapshotResponse billingHealthSnapshot = billingSchedulerService.getBillingHealthSnapshot(tenantId);
        response.setBillingHealthSnapshot(billingHealthSnapshot);
        return response;
    }

    @Transactional
    public PlatformCompany360Response updateCompanyOwnerForAuthenticatedUser(String authenticatedName,
                                                                             int tenantId,
                                                                             int newOwnerUserId) {
        User actor = platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.lifecycle.write");
        requireCompany(tenantId);
        ConfigurationAdminUserSummary newOwner = dbConfigurationAdmin.getCompanyOwnerCandidates(tenantId)
                .stream()
                .filter((user) -> user.getUserId() == newOwnerUserId)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "OWNER_USER_NOT_FOUND",
                        "Selected owner user was not found in this tenant or unassigned user pool"
                ));

        int rows = dbCompany.updateCompanyOwner(tenantId, newOwner.getUserId());
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found");
        }

        dbConfigurationAdmin.getTenantRoleAssignments(tenantId, null)
                .stream()
                .filter((assignment) -> "Owner".equalsIgnoreCase(assignment.getRoleId()))
                .filter((assignment) -> assignment.getUserId() != newOwner.getUserId())
                .forEach((assignment) -> dbConfigurationAdmin.deactivateTenantRoleAssignment(assignment.getAssignmentId()));
        dbConfigurationAdmin.upsertTenantRoleAssignment(
                tenantId,
                newOwner.getUserId(),
                "Owner",
                "company",
                null,
                actor.getUserId(),
                "admin"
        );

        return getCompany360ForAuthenticatedUser(authenticatedName, tenantId);
    }

    public ArrayList<PlatformCompanyBranchSummary> getCompanyBranchesForAuthenticatedUser(String authenticatedName, int tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        requireCompany(tenantId);
        return dbPlatformAdminReadModels.getCompanyBranches(tenantId);
    }

    public ArrayList<ConfigurationAdminUserSummary> getCompanyUsersForAuthenticatedUser(String authenticatedName,
                                                                                        int tenantId,
                                                                                        Integer branchId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        requireCompany(tenantId);
        if (branchId != null) {
            ensureBranchBelongsToTenant(tenantId, branchId);
        }
        return new ArrayList<>(dbConfigurationAdmin.getUsersForTenant(tenantId, branchId));
    }

    public ArrayList<ConfigurationAdminUserSummary> getCompanyOwnerCandidatesForAuthenticatedUser(String authenticatedName,
                                                                                                   int tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        requireCompany(tenantId);
        return new ArrayList<>(dbConfigurationAdmin.getCompanyOwnerCandidates(tenantId));
    }

    public ArrayList<ConfigurationAdminUserSummary> getPlatformUsersForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        return new ArrayList<>(dbConfigurationAdmin.getPlatformUsers());
    }

    public String resetUserPasswordForAuthenticatedUser(String authenticatedName,
                                                        String userName,
                                                        String password,
                                                        boolean passwordResetRequired) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.lifecycle.write");
        return dbUsers.adminResetUserPassword(userName, 0, password, passwordResetRequired);
    }

    public ArrayList<PlatformCompanyBranchSubscriptionItem> getCompanyBranchSubscriptionsForAuthenticatedUser(String authenticatedName,
                                                                                                             int tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        requireCompany(tenantId);
        return dbPlatformAdminReadModels.getCompanyBranchSubscriptions(tenantId);
    }

    public PlatformTenantBillingWorkflowSummaryResponse getTenantBillingWorkflowSummaryForAuthenticatedUser(String authenticatedName,
                                                                                                            int tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        requireCompany(tenantId);

        PlatformBillingHealthSnapshotResponse billingHealthSnapshot = billingSchedulerService.getBillingHealthSnapshot(tenantId);
        ArrayList<PlatformAuditEventItem> recentBillingAuditEvents =
                platformAdminAuditService.getRecentBillingAuditEventsForAuthenticatedUser(authenticatedName, tenantId, 10);
        ArrayList<PlatformSupportNoteItem> recentBillingSupportNotes =
                platformSupportService.getRecentBillingNotesForAuthenticatedUser(authenticatedName, tenantId, 10);

        return new PlatformTenantBillingWorkflowSummaryResponse(
                tenantId,
                billingHealthSnapshot,
                platformSupportService.countBillingNotesForAuthenticatedUser(authenticatedName, tenantId, null),
                platformSupportService.countBillingNotesForAuthenticatedUser(authenticatedName, tenantId, "restricted"),
                recentBillingAuditEvents,
                recentBillingSupportNotes,
                new java.sql.Timestamp(System.currentTimeMillis())
        );
    }

    public ArrayList<Client> getCompanyClientsForAuthenticatedUser(String authenticatedName,
                                                                   int tenantId,
                                                                   Integer branchId,
                                                                   Integer max) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        Company company = requireCompany(tenantId);
        if (branchId != null) {
            ensureBranchBelongsToTenant(tenantId, branchId);
        }
        int safeMax = sanitizeLimit(max);
        return dbClient.getLatestClients(company.getCompanyId(), safeMax, branchId == null ? 0 : branchId);
    }

    public ArrayList<Product> getCompanyProductsForAuthenticatedUser(String authenticatedName,
                                                                     int tenantId,
                                                                     Integer branchId,
                                                                     Integer max) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        Company company = requireCompany(tenantId);
        int safeMax = sanitizeLimit(max);
        ArrayList<Product> products = new ArrayList<>();

        if (branchId != null) {
            ensureBranchBelongsToTenant(tenantId, branchId);
            products.addAll(dbPosProduct.getProductsAllRange(String.valueOf(branchId), company.getCompanyId(), DEFAULT_PRODUCT_FILTER));
        } else {
            List<Branch> branches = dbBranch.getBranchByCompanyId(tenantId);
            for (Branch branch : branches) {
                products.addAll(
                        dbPosProduct.getProductsAllRange(
                                String.valueOf(branch.getBranchID()),
                                company.getCompanyId(),
                                DEFAULT_PRODUCT_FILTER
                        )
                );
            }
        }

        products.sort(
                Comparator.comparing(Product::getBuyingDay, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Product::getProductId, Comparator.reverseOrder())
        );

        if (products.size() > safeMax) {
            return new ArrayList<>(products.subList(0, safeMax));
        }
        return products;
    }

    private Company requireCompany(int tenantId) {
        Company company = dbCompany.getCompanyById(tenantId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return company;
    }

    private void ensureBranchBelongsToTenant(int tenantId, int branchId) {
        Branch branch = dbBranch.getBranchById(branchId);
        if (branch.getBranchOfCompanyId() != tenantId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_SCOPE_INVALID", "Branch does not belong to the requested tenant");
        }
    }

    private int sanitizeLimit(Integer max) {
        if (max == null) {
            return DEFAULT_DETAIL_LIMIT;
        }
        return Math.max(1, Math.min(max, MAX_DETAIL_LIMIT));
    }
}
