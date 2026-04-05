package com.example.valueinsoftbackend.Service;

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
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanyBranchSummary;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanySubscriptionItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminReadModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompaniesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompany360Response;
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
    private final PlatformAuthorizationService platformAuthorizationService;

    public PlatformAdminCompanyService(DbPlatformAdminReadModels dbPlatformAdminReadModels,
                                       DbCompany dbCompany,
                                       DbBranch dbBranch,
                                       DbConfigurationAdmin dbConfigurationAdmin,
                                       DbClient dbClient,
                                       DbPosProduct dbPosProduct,
                                       PlatformAuthorizationService platformAuthorizationService) {
        this.dbPlatformAdminReadModels = dbPlatformAdminReadModels;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.dbConfigurationAdmin = dbConfigurationAdmin;
        this.dbClient = dbClient;
        this.dbPosProduct = dbPosProduct;
        this.platformAuthorizationService = platformAuthorizationService;
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
        return dbPlatformAdminReadModels.getCompany360(tenantId);
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

    public ArrayList<PlatformCompanySubscriptionItem> getCompanySubscriptionsForAuthenticatedUser(String authenticatedName,
                                                                                                   int tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        requireCompany(tenantId);
        return dbPlatformAdminReadModels.getCompanySubscriptions(tenantId);
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
