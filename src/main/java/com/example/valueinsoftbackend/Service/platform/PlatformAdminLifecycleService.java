package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminOperations;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformLifecycleActionResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.PlatformLifecycleActionRequest;
import com.example.valueinsoftbackend.Model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PlatformAdminLifecycleService {

    private final PlatformAuthorizationService platformAuthorizationService;
    private final DbTenants dbTenants;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final DbPlatformAdminOperations dbPlatformAdminOperations;
    private final ObjectMapper objectMapper;

    public PlatformAdminLifecycleService(PlatformAuthorizationService platformAuthorizationService,
                                         DbTenants dbTenants,
                                         DbCompany dbCompany,
                                         DbBranch dbBranch,
                                         DbPlatformAdminOperations dbPlatformAdminOperations,
                                         ObjectMapper objectMapper) {
        this.platformAuthorizationService = platformAuthorizationService;
        this.dbTenants = dbTenants;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.dbPlatformAdminOperations = dbPlatformAdminOperations;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlatformLifecycleActionResponse suspendCompanyForAuthenticatedUser(String authenticatedName,
                                                                             int tenantId,
                                                                             PlatformLifecycleActionRequest request) {
        User actor = platformAuthorizationService.requirePlatformCapability(
                authenticatedName,
                "platform.company.lifecycle.write"
        );
        TenantConfig tenant = requireTenant(tenantId);
        Company company = requireCompany(tenantId);

        String previousStatus = normalize(tenant.getStatus());
        if ("archived".equalsIgnoreCase(previousStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "TENANT_STATUS_IMMUTABLE", "Archived tenants cannot be suspended");
        }

        boolean changed = !"suspended".equalsIgnoreCase(previousStatus);
        Timestamp processedAt = now();
        if (changed) {
            dbPlatformAdminOperations.updateTenantStatus(tenantId, "suspended");
            dbPlatformAdminOperations.insertTenantLifecycleEvent(
                    tenantId,
                    "suspend",
                    previousStatus,
                    "suspended",
                    normalize(request.getReason()),
                    normalizeNullable(request.getNote()),
                    actor.getUserId(),
                    actor.getUserName(),
                    "platform_admin"
            );
        }

        dbPlatformAdminOperations.insertAuditLog(
                actor.getUserId(),
                actor.getUserName(),
                "platform.company.lifecycle.write",
                "platform.company.suspend",
                tenantId,
                null,
                toJson(buildMap(
                        "reason", normalize(request.getReason()),
                        "note", normalizeNullable(request.getNote())
                )),
                toJson(buildMap(
                        "companyName", company.getCompanyName(),
                        "previousStatus", previousStatus,
                        "newStatus", "suspended"
                )),
                changed ? "success" : "rejected",
                null
        );

        return new PlatformLifecycleActionResponse(
                "tenant",
                tenantId,
                null,
                "suspend",
                previousStatus,
                "suspended",
                changed,
                normalize(request.getReason()),
                normalizeNullable(request.getNote()),
                actor.getUserName(),
                processedAt
        );
    }

    @Transactional
    public PlatformLifecycleActionResponse resumeCompanyForAuthenticatedUser(String authenticatedName,
                                                                            int tenantId,
                                                                            PlatformLifecycleActionRequest request) {
        User actor = platformAuthorizationService.requirePlatformCapability(
                authenticatedName,
                "platform.company.lifecycle.write"
        );
        TenantConfig tenant = requireTenant(tenantId);
        Company company = requireCompany(tenantId);

        String previousStatus = normalize(tenant.getStatus());
        if ("archived".equalsIgnoreCase(previousStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "TENANT_STATUS_IMMUTABLE", "Archived tenants cannot be resumed");
        }
        if (!"suspended".equalsIgnoreCase(previousStatus) && !"active".equalsIgnoreCase(previousStatus)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TENANT_STATUS_TRANSITION_INVALID",
                    "Tenant can only be resumed from suspended status"
            );
        }

        boolean changed = !"active".equalsIgnoreCase(previousStatus);
        Timestamp processedAt = now();
        if (changed) {
            dbPlatformAdminOperations.updateTenantStatus(tenantId, "active");
            dbPlatformAdminOperations.insertTenantLifecycleEvent(
                    tenantId,
                    "resume",
                    previousStatus,
                    "active",
                    normalize(request.getReason()),
                    normalizeNullable(request.getNote()),
                    actor.getUserId(),
                    actor.getUserName(),
                    "platform_admin"
            );
        }

        dbPlatformAdminOperations.insertAuditLog(
                actor.getUserId(),
                actor.getUserName(),
                "platform.company.lifecycle.write",
                "platform.company.resume",
                tenantId,
                null,
                toJson(buildMap(
                        "reason", normalize(request.getReason()),
                        "note", normalizeNullable(request.getNote())
                )),
                toJson(buildMap(
                        "companyName", company.getCompanyName(),
                        "previousStatus", previousStatus,
                        "newStatus", "active"
                )),
                changed ? "success" : "rejected",
                null
        );

        return new PlatformLifecycleActionResponse(
                "tenant",
                tenantId,
                null,
                "resume",
                previousStatus,
                "active",
                changed,
                normalize(request.getReason()),
                normalizeNullable(request.getNote()),
                actor.getUserName(),
                processedAt
        );
    }

    @Transactional
    public PlatformLifecycleActionResponse lockBranchForAuthenticatedUser(String authenticatedName,
                                                                         int branchId,
                                                                         PlatformLifecycleActionRequest request) {
        User actor = platformAuthorizationService.requirePlatformCapability(
                authenticatedName,
                "platform.branch.lifecycle.write"
        );
        Branch branch = dbBranch.getBranchById(branchId);
        dbPlatformAdminOperations.ensureBranchRuntimeState(branchId, branch.getBranchOfCompanyId());
        DbPlatformAdminOperations.BranchRuntimeStateRecord state = dbPlatformAdminOperations.getBranchRuntimeState(branchId);

        String previousStatus = state == null ? "active" : normalize(state.getStatus());
        if ("archived".equalsIgnoreCase(previousStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_STATUS_IMMUTABLE", "Archived branches cannot be locked");
        }

        boolean changed = !"locked".equalsIgnoreCase(previousStatus);
        String targetReason = hasText(request.getReason()) ? normalize(request.getReason()) : "platform_admin_lock";
        Timestamp processedAt = now();
        if (changed) {
            dbPlatformAdminOperations.upsertBranchRuntimeState(
                    branchId,
                    branch.getBranchOfCompanyId(),
                    "locked",
                    targetReason,
                    actor.getUserId()
            );
            dbPlatformAdminOperations.insertBranchLifecycleEvent(
                    branch.getBranchOfCompanyId(),
                    branchId,
                    "lock",
                    previousStatus,
                    "locked",
                    normalize(request.getReason()),
                    normalizeNullable(request.getNote()),
                    actor.getUserId(),
                    actor.getUserName(),
                    "platform_admin"
            );
        }

        dbPlatformAdminOperations.insertAuditLog(
                actor.getUserId(),
                actor.getUserName(),
                "platform.branch.lifecycle.write",
                "platform.branch.lock",
                branch.getBranchOfCompanyId(),
                branchId,
                toJson(buildMap(
                        "reason", normalize(request.getReason()),
                        "note", normalizeNullable(request.getNote())
                )),
                toJson(buildMap(
                        "branchName", branch.getBranchName(),
                        "previousStatus", previousStatus,
                        "newStatus", "locked"
                )),
                changed ? "success" : "rejected",
                null
        );

        return new PlatformLifecycleActionResponse(
                "branch",
                branch.getBranchOfCompanyId(),
                branchId,
                "lock",
                previousStatus,
                "locked",
                changed,
                normalize(request.getReason()),
                normalizeNullable(request.getNote()),
                actor.getUserName(),
                processedAt
        );
    }

    @Transactional
    public PlatformLifecycleActionResponse unlockBranchForAuthenticatedUser(String authenticatedName,
                                                                           int branchId,
                                                                           PlatformLifecycleActionRequest request) {
        User actor = platformAuthorizationService.requirePlatformCapability(
                authenticatedName,
                "platform.branch.lifecycle.write"
        );
        Branch branch = dbBranch.getBranchById(branchId);
        dbPlatformAdminOperations.ensureBranchRuntimeState(branchId, branch.getBranchOfCompanyId());
        DbPlatformAdminOperations.BranchRuntimeStateRecord state = dbPlatformAdminOperations.getBranchRuntimeState(branchId);

        String previousStatus = state == null ? "active" : normalize(state.getStatus());
        if ("archived".equalsIgnoreCase(previousStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_STATUS_IMMUTABLE", "Archived branches cannot be unlocked");
        }
        if (!"locked".equalsIgnoreCase(previousStatus) && !"active".equalsIgnoreCase(previousStatus)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BRANCH_STATUS_TRANSITION_INVALID",
                    "Branch can only be unlocked from locked status"
            );
        }

        boolean changed = !"active".equalsIgnoreCase(previousStatus);
        String targetReason = hasText(request.getReason()) ? normalize(request.getReason()) : "platform_admin_unlock";
        Timestamp processedAt = now();
        if (changed) {
            dbPlatformAdminOperations.upsertBranchRuntimeState(
                    branchId,
                    branch.getBranchOfCompanyId(),
                    "active",
                    targetReason,
                    null
            );
            dbPlatformAdminOperations.insertBranchLifecycleEvent(
                    branch.getBranchOfCompanyId(),
                    branchId,
                    "unlock",
                    previousStatus,
                    "active",
                    normalize(request.getReason()),
                    normalizeNullable(request.getNote()),
                    actor.getUserId(),
                    actor.getUserName(),
                    "platform_admin"
            );
        }

        dbPlatformAdminOperations.insertAuditLog(
                actor.getUserId(),
                actor.getUserName(),
                "platform.branch.lifecycle.write",
                "platform.branch.unlock",
                branch.getBranchOfCompanyId(),
                branchId,
                toJson(buildMap(
                        "reason", normalize(request.getReason()),
                        "note", normalizeNullable(request.getNote())
                )),
                toJson(buildMap(
                        "branchName", branch.getBranchName(),
                        "previousStatus", previousStatus,
                        "newStatus", "active"
                )),
                changed ? "success" : "rejected",
                null
        );

        return new PlatformLifecycleActionResponse(
                "branch",
                branch.getBranchOfCompanyId(),
                branchId,
                "unlock",
                previousStatus,
                "active",
                changed,
                normalize(request.getReason()),
                normalizeNullable(request.getNote()),
                actor.getUserName(),
                processedAt
        );
    }

    private TenantConfig requireTenant(int tenantId) {
        TenantConfig tenant = dbTenants.getTenantById(tenantId);
        if (tenant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found");
        }
        return tenant;
    }

    private Company requireCompany(int tenantId) {
        Company company = dbCompany.getCompanyById(tenantId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return company;
    }

    private Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String toJson(Map<String, Object> values) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            sanitized.put(entry.getKey(), entry.getValue());
        }
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PLATFORM_AUDIT_SERIALIZATION_FAILED",
                    "Could not serialize platform admin audit payload"
            );
        }
    }

    private Map<String, Object> buildMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
