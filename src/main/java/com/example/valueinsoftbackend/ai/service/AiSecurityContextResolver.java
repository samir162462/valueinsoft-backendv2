package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiSecurityContextResolver {

    private final DbUsers dbUsers;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;

    public AiSecurityContextResolver(DbUsers dbUsers, DbCompany dbCompany, DbBranch dbBranch) {
        this.dbUsers = dbUsers;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
    }

    public AiSecurityContext resolveCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return resolve(authentication.getName());
    }

    public AiSecurityContext resolve(Principal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return resolve(principal.getName());
    }

    public long getCurrentCompanyId() {
        return resolveCurrent().companyId();
    }

    public long getCurrentUserId() {
        return resolveCurrent().userId();
    }

    public String getCurrentUsername() {
        return resolveCurrent().username();
    }

    public List<String> getCurrentRoles() {
        return List.of(resolveCurrent().role());
    }

    public Set<Long> getAllowedBranchIds() {
        return resolveCurrent().allowedBranchIds();
    }

    public void validateBranchAccess(AiSecurityContext context, Long branchId) {
        if (branchId == null) {
            return;
        }
        if (!context.allowedBranchIds().contains(branchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED", "Branch access denied");
        }
        Branch branch = dbBranch.getBranchById(Math.toIntExact(branchId));
        if (branch.getBranchOfCompanyId() != context.companyId()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED", "Branch access denied");
        }
    }

    private AiSecurityContext resolve(String principalName) {
        String username = extractBaseUserName(principalName);
        User user = dbUsers.getUser(username);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        Company company = resolveCompany(user);
        Set<Long> branchIds = company.getBranchList() == null
                ? Set.of()
                : company.getBranchList().stream()
                .map(Branch::getBranchID)
                .map(Integer::longValue)
                .collect(Collectors.toUnmodifiableSet());
        Long defaultBranchId = user.getBranchId() > 0 ? (long) user.getBranchId() : branchIds.stream().findFirst().orElse(null);

        return new AiSecurityContext(
                company.getCompanyId(),
                user.getUserId(),
                user.getUserName(),
                user.getRole(),
                defaultBranchId,
                branchIds
        );
    }

    private Company resolveCompany(User user) {
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

        throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_CONTEXT_NOT_FOUND", "Could not resolve tenant context");
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }
}
