package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Model.Configuration.NavigationItemConfig;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Resolves tenant and branch context for the authenticated user before delegating
 * to the configuration aggregation service.
 */
@Service
public class AuthenticatedEffectiveConfigurationService {

    private final DbUsers dbUsers;
    private final DbCompany dbCompany;
    private final EffectiveConfigurationService effectiveConfigurationService;

    public AuthenticatedEffectiveConfigurationService(DbUsers dbUsers,
                                                     DbCompany dbCompany,
                                                     EffectiveConfigurationService effectiveConfigurationService) {
        this.dbUsers = dbUsers;
        this.dbCompany = dbCompany;
        this.effectiveConfigurationService = effectiveConfigurationService;
    }

    /**
     * Resolves the authenticated user, tenant context, and active branch before loading
     * the effective configuration payload.
     *
     * @param authenticatedName principal name from Spring Security, potentially including the legacy role suffix
     * @param requestedTenantId optional tenant override from the request
     * @param requestedBranchId optional branch override from the request
     * @return effective configuration for the resolved tenant and active branch context
     */
    public EffectiveConfiguration getEffectiveConfigurationForAuthenticatedUser(String authenticatedName,
                                                                                Integer requestedTenantId,
                                                                                Integer requestedBranchId) {
        String userName = extractBaseUserName(authenticatedName);
        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        ResolvedTenantContext context = resolveTenantContext(user, requestedTenantId, requestedBranchId);
        return effectiveConfigurationService.getEffectiveConfiguration(context.tenantId, user.getUserId(), context.activeBranchId);
    }

    /**
     * Resolves only the effective capability list for the authenticated user.
     */
    public ArrayList<ResolvedCapabilityConfig> getEffectiveCapabilitiesForAuthenticatedUser(String authenticatedName,
                                                                                            Integer requestedTenantId,
                                                                                            Integer requestedBranchId) {
        String userName = extractBaseUserName(authenticatedName);
        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        ResolvedTenantContext context = resolveTenantContext(user, requestedTenantId, requestedBranchId);
        return effectiveConfigurationService.getEffectiveCapabilities(context.tenantId, user.getUserId(), context.activeBranchId);
    }

    /**
     * Resolves the navigation projection for the authenticated user.
     */
    public ArrayList<NavigationItemConfig> getNavigationForAuthenticatedUser(String authenticatedName,
                                                                             Integer requestedTenantId,
                                                                             Integer requestedBranchId) {
        String userName = extractBaseUserName(authenticatedName);
        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        ResolvedTenantContext context = resolveTenantContext(user, requestedTenantId, requestedBranchId);
        return effectiveConfigurationService.getNavigationItems(context.tenantId, user.getUserId(), context.activeBranchId);
    }

    /**
     * Resolves tenant and branch context for the authenticated user using the existing
     * legacy company and branch relationships until JWT context becomes richer.
     */
    private ResolvedTenantContext resolveTenantContext(User user, Integer requestedTenantId, Integer requestedBranchId) {
        Company company;
        if (requestedTenantId != null) {
            company = dbCompany.getCompanyById(requestedTenantId);
            if (company == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant company not found");
            }
            if (!userBelongsToCompany(user, company)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "User does not belong to the requested tenant");
            }
        } else {
            company = resolveLegacyCompany(user);
        }

        Integer activeBranchId = requestedBranchId;
        if (activeBranchId == null && user.getBranchId() > 0) {
            activeBranchId = user.getBranchId();
        }
        if (activeBranchId == null) {
            activeBranchId = firstCompanyBranchId(company);
        }

        if (activeBranchId != null && !branchBelongsToCompany(company, activeBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED", "Branch does not belong to the resolved tenant");
        }

        return new ResolvedTenantContext(company.getCompanyId(), activeBranchId);
    }

    /**
     * Falls back to legacy company ownership or branch membership when no tenant id is explicitly supplied.
     */
    private Company resolveLegacyCompany(User user) {
        if (user.getBranchId() > 0) {
            Company company = dbCompany.getCompanyAndBranchesByUserName(user.getUserName());
            if (company != null) {
                return company;
            }
        }

        Company company = dbCompany.getCompanyByOwnerId(user.getUserId());
        if (company != null) {
            return company;
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_CONTEXT_NOT_FOUND", "Could not resolve tenant context for user");
    }

    /**
     * Checks whether the current legacy user identity belongs to the requested company.
     */
    private boolean userBelongsToCompany(User user, Company company) {
        if (company.getOwnerId() == user.getUserId()) {
            return true;
        }
        if (user.getBranchId() <= 0) {
            return false;
        }
        return branchBelongsToCompany(company, user.getBranchId());
    }

    /**
     * Verifies that a branch is owned by the provided company aggregate.
     */
    private boolean branchBelongsToCompany(Company company, int branchId) {
        ArrayList<Branch> branches = company.getBranchList();
        if (branches == null) {
            return false;
        }
        for (Branch branch : branches) {
            if (branch.getBranchID() == branchId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Uses the first known branch when the user has no explicit branch context.
     */
    private Integer firstCompanyBranchId(Company company) {
        ArrayList<Branch> branches = company.getBranchList();
        if (branches == null || branches.isEmpty()) {
            return null;
        }
        return branches.get(0).getBranchID();
    }

    /**
     * Removes the legacy "username : role" suffix used by the current authentication flow.
     */
    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }

    private static class ResolvedTenantContext {
        private final int tenantId;
        private final Integer activeBranchId;

        private ResolvedTenantContext(int tenantId, Integer activeBranchId) {
            this.tenantId = tenantId;
            this.activeBranchId = activeBranchId;
        }
    }
}
