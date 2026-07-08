package com.example.valueinsoftbackend.security;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService.ResolvedTenantContext;
import com.example.valueinsoftbackend.Service.security.TenantScopeGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-2 unit tests for {@link TenantScopeGuard}: the guard must return the identity-derived
 * authoritative scope for a company the user belongs to, and must reject a foreign company/branch
 * by propagating the resolver's TENANT_ACCESS_DENIED / BRANCH_ACCESS_DENIED (403).
 */
class TenantScopeGuardTest {

    private static final String OWNER_A = "ownerA : Owner";
    private static final int COMPANY_A = 100;
    private static final int BRANCH_A = 11;
    private static final int COMPANY_B = 200;
    private static final int BRANCH_B = 22;

    private AuthenticatedEffectiveConfigurationService contextService;
    private TenantScopeGuard guard;

    @BeforeEach
    void setUp() {
        contextService = Mockito.mock(AuthenticatedEffectiveConfigurationService.class);
        guard = new TenantScopeGuard(contextService);
    }

    @Test
    void requireScope_returnsIdentityDerivedScope_forOwnCompany() {
        ResolvedTenantContext context = mock(ResolvedTenantContext.class);
        when(context.getTenantId()).thenReturn(COMPANY_A);
        when(context.getActiveBranchId()).thenReturn(BRANCH_A);
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(OWNER_A), eq(COMPANY_A), eq(BRANCH_A)))
                .thenReturn(context);

        TenantScopeGuard.ResolvedTenantScope scope = guard.requireScope(OWNER_A, COMPANY_A, BRANCH_A);

        assertEquals(COMPANY_A, scope.companyId());
        assertEquals(BRANCH_A, scope.branchId());
    }

    @Test
    void requireScope_throwsTenantAccessDenied_forForeignCompany() {
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(OWNER_A), eq(COMPANY_B), eq(BRANCH_B)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED",
                        "User does not belong to the requested tenant"));

        ApiException ex = assertThrows(ApiException.class, () -> guard.requireScope(OWNER_A, COMPANY_B, BRANCH_B));
        assertEquals("TENANT_ACCESS_DENIED", ex.getCode());
    }

    @Test
    void requireScope_throwsBranchAccessDenied_forBranchOutsideCompany() {
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(OWNER_A), eq(COMPANY_A), eq(BRANCH_B)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED",
                        "Branch does not belong to the resolved tenant"));

        ApiException ex = assertThrows(ApiException.class, () -> guard.requireScope(OWNER_A, COMPANY_A, BRANCH_B));
        assertEquals("BRANCH_ACCESS_DENIED", ex.getCode());
    }

    @Test
    void requireCompanyScope_fallsBackToIdentityCompany_whenRequestedCompanyIsNull() {
        ResolvedTenantContext context = mock(ResolvedTenantContext.class);
        when(context.getTenantId()).thenReturn(COMPANY_A);
        when(context.getActiveBranchId()).thenReturn(BRANCH_A);
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(OWNER_A), eq(null), eq(null)))
                .thenReturn(context);

        TenantScopeGuard.ResolvedTenantScope scope = guard.requireCompanyScope(OWNER_A, null);

        assertEquals(COMPANY_A, scope.companyId());
    }
}
