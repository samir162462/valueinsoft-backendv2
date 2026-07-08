package com.example.valueinsoftbackend.Service.security;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Enforces backend capability checks using the resolved configuration model
 * instead of hardcoded legacy role names.
 */
@Service
@Slf4j
public class AuthorizationService {

    private final AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService;

    public AuthorizationService(AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService) {
        this.authenticatedEffectiveConfigurationService = authenticatedEffectiveConfigurationService;
    }

    /**
     * Ensures that the authenticated user has the requested capability in the resolved tenant and branch context.
     *
     * @param authenticatedName principal name from the current authentication context
     * @param tenantId optional tenant override
     * @param branchId optional active branch override
     * @param capabilityKey stable capability key to enforce
     */
    public void assertAuthenticatedCapability(String authenticatedName,
                                             Integer tenantId,
                                             Integer branchId,
                                             String capabilityKey) {
        if (hasAuthenticatedCapability(authenticatedName, tenantId, branchId, capabilityKey)) {
            return;
        }

        log.warn("Capability DENIED for user {}: key={}, tenant={}, branch={}", 
                authenticatedName, capabilityKey, tenantId, branchId);
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "CAPABILITY_DENIED",
                "Missing required capability: " + capabilityKey
        );
    }

    /**
     * Returns whether the authenticated user currently has the requested capability.
     */
    public boolean hasAuthenticatedCapability(String authenticatedName,
                                             Integer tenantId,
                                             Integer branchId,
                                             String capabilityKey) {
        // SECURITY (P0-1 fix): always resolve the tenant/branch context FIRST.
        // getEffectiveCapabilitiesForAuthenticatedUser -> resolveTenantContext enforces
        // tenant and branch membership and throws TENANT_ACCESS_DENIED / BRANCH_ACCESS_DENIED
        // when the authenticated user does not belong to the requested company or branch.
        // No role -- including Owner -- may skip this check. Previously the Owner branch
        // short-circuited before this call, allowing an owner of one company to act on
        // any other company by supplying a foreign companyId.
        ArrayList<ResolvedCapabilityConfig> capabilities =
                authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                        authenticatedName,
                        tenantId,
                        branchId
                );

        // Owners retain broad access, but ONLY within a tenant/branch they belong to
        // (membership was verified above). This preserves in-tenant owner privileges
        // while closing the cross-tenant bypass.
        if (isOwnerPrincipal(authenticatedName)) {
            log.info("Capability Check | User: {} | Key: {} | Branch: {} | Allowed: true | Reason: owner_role_in_tenant",
                    authenticatedName, capabilityKey, branchId);
            return true;
        }

        boolean found = false;
        for (ResolvedCapabilityConfig capability : capabilities) {
            if (capabilityKey.equals(capability.getCapabilityKey())
                    && "allow".equalsIgnoreCase(capability.getGrantMode())) {
                found = true;
                break;
            }
        }

        log.info("Capability Check | User: {} | Key: {} | Branch: {} | Allowed: {}", 
                authenticatedName, capabilityKey, branchId, found);

        return found;
    }

    /**
     * Enforces a self-scoped capability and verifies that the authenticated user is acting only on their own account.
     */
    public void assertSelfCapability(String authenticatedName, String targetUserName, String capabilityKey) {
        String resolvedAuthenticatedName = extractBaseUserName(authenticatedName);
        String resolvedTargetUserName = targetUserName == null ? "" : targetUserName.trim();

        if (!resolvedAuthenticatedName.equals(resolvedTargetUserName)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SELF_ACCESS_DENIED",
                    "This action is only allowed for the authenticated user"
            );
        }

        assertAuthenticatedCapability(authenticatedName, null, null, capabilityKey);
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }

    private boolean isOwnerPrincipal(String value) {
        if (value == null || !value.contains(" : ")) {
            return false;
        }

        String[] parts = value.split(" : ", 2);
        return parts.length == 2 && "Owner".equalsIgnoreCase(parts[1].trim());
    }
}
