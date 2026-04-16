package com.example.valueinsoftbackend.Service;

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
        ArrayList<ResolvedCapabilityConfig> capabilities =
                authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                        authenticatedName,
                        tenantId,
                        branchId
                );

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
}
