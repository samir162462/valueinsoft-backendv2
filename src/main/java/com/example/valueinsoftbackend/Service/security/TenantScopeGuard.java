package com.example.valueinsoftbackend.Service.security;

import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService.ResolvedTenantContext;
import org.springframework.stereotype.Service;

/**
 * SECURITY (P0-2): centralizes tenant/branch scope resolution and validation.
 *
 * <p>Controllers historically trusted a {@code companyId} (and sometimes {@code branchId})
 * supplied directly in the request path/body and forwarded it to services. This guard resolves
 * the tenant scope bound to the authenticated identity and validates that any request-supplied
 * company/branch actually belongs to the user, returning the authoritative, identity-derived
 * scope for downstream use. It throws TENANT_ACCESS_DENIED / BRANCH_ACCESS_DENIED (HTTP 403)
 * for a company or branch the user does not belong to.</p>
 *
 * <p>This is defense-in-depth that complements {@link AuthorizationService}: even if a capability
 * check is misconfigured, weakened, or omitted on a future endpoint, the tenant boundary is still
 * enforced from identity rather than from the request.</p>
 */
@Service
public class TenantScopeGuard {

    private final AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService;

    public TenantScopeGuard(AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService) {
        this.authenticatedEffectiveConfigurationService = authenticatedEffectiveConfigurationService;
    }

    /**
     * Resolves and validates the tenant scope for the authenticated principal against the
     * request-supplied companyId/branchId. Returns the authoritative (identity-derived) companyId
     * and the validated branchId. Throws an {@code ApiException} (403) if the request targets a
     * company or branch the user does not belong to.
     *
     * @param authenticatedName the Spring Security principal name (may carry the legacy " : Role" suffix)
     * @param requestedCompanyId companyId supplied by the request (nullable — falls back to the user's own tenant)
     * @param requestedBranchId branchId supplied by the request (nullable)
     */
    public ResolvedTenantScope requireScope(String authenticatedName,
                                            Integer requestedCompanyId,
                                            Integer requestedBranchId) {
        ResolvedTenantContext context = authenticatedEffectiveConfigurationService
                .resolveTenantContextForAuthenticatedUser(authenticatedName, requestedCompanyId, requestedBranchId);
        return new ResolvedTenantScope(context.getTenantId(), context.getActiveBranchId());
    }

    /**
     * Convenience overload for endpoints that only need company-level scope.
     */
    public ResolvedTenantScope requireCompanyScope(String authenticatedName, Integer requestedCompanyId) {
        return requireScope(authenticatedName, requestedCompanyId, null);
    }

    /**
     * Authoritative, identity-derived tenant scope.
     *
     * @param companyId the resolved companyId (never trusts the raw request value beyond validation)
     * @param branchId the validated active branchId (may be null when no branch context applies)
     */
    public record ResolvedTenantScope(int companyId, Integer branchId) {
    }
}
