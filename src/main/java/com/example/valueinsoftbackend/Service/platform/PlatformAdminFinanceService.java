package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminFinanceReadModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformClientReceiptsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformExpensesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupplierReceiptsPageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminFinanceService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final DbPlatformAdminFinanceReadModels dbPlatformAdminFinanceReadModels;
    private final PlatformAuthorizationService platformAuthorizationService;

    public PlatformAdminFinanceService(DbCompany dbCompany,
                                       DbBranch dbBranch,
                                       DbPlatformAdminFinanceReadModels dbPlatformAdminFinanceReadModels,
                                       PlatformAuthorizationService platformAuthorizationService) {
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.dbPlatformAdminFinanceReadModels = dbPlatformAdminFinanceReadModels;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    public PlatformExpensesPageResponse getCompanyExpensesForAuthenticatedUser(String authenticatedName,
                                                                               int tenantId,
                                                                               Integer branchId,
                                                                               int page,
                                                                               int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        Company company = requireCompany(tenantId);
        if (branchId != null) {
            ensureBranchBelongsToTenant(tenantId, branchId);
        }
        return dbPlatformAdminFinanceReadModels.getCompanyExpenses(
                company.getCompanyId(),
                branchId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformClientReceiptsPageResponse getCompanyClientReceiptsForAuthenticatedUser(String authenticatedName,
                                                                                            int tenantId,
                                                                                            Integer branchId,
                                                                                            int page,
                                                                                            int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        Company company = requireCompany(tenantId);
        if (branchId != null) {
            ensureBranchBelongsToTenant(tenantId, branchId);
        }
        return dbPlatformAdminFinanceReadModels.getCompanyClientReceipts(
                company.getCompanyId(),
                branchId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    public PlatformSupplierReceiptsPageResponse getCompanySupplierReceiptsForAuthenticatedUser(String authenticatedName,
                                                                                                int tenantId,
                                                                                                Integer branchId,
                                                                                                int page,
                                                                                                int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.company.read");
        Company company = requireCompany(tenantId);
        if (branchId != null) {
            ensureBranchBelongsToTenant(tenantId, branchId);
        }
        return dbPlatformAdminFinanceReadModels.getCompanySupplierReceipts(
                company.getCompanyId(),
                branchId,
                sanitizePage(page),
                sanitizeSize(size)
        );
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
