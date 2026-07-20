package com.example.valueinsoftbackend.security;

import com.example.valueinsoftbackend.AbstractIntegrationTest;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.SupplierService;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService.ResolvedTenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-1 / P0-2 endpoint-level regression across two tenants.
 *
 * <ul>
 *   <li>Company A = 100 (Owner A, Branch A = 11)</li>
 *   <li>Company B = 200 (Owner B, Branch B = 22)</li>
 * </ul>
 *
 * The REAL {@code AuthorizationService} and {@code TenantScopeGuard} beans are used (NOT mocked).
 * Their shared membership collaborator {@link AuthenticatedEffectiveConfigurationService} is mocked
 * to reproduce the real resolver: it throws TENANT_ACCESS_DENIED for a company Owner A does not
 * belong to, and returns a resolved context for Owner A's own company. {@link SupplierService} is
 * mocked to observe whether the request reaches the tenant data path.
 *
 * <p>Endpoint under test: {@code GET /suppliers/all/{companyId}/{branchId}} — the systemic
 * "request-supplied companyId" pattern (P0-2). {@code TenantScopeGuard.requireScope} resolves the
 * identity-bound scope BEFORE the capability check, so a foreign company yields 403 and never
 * reaches {@code SupplierService}.</p>
 */
class OwnerCrossTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final int COMPANY_A = 100;
    private static final int BRANCH_A = 11;
    private static final int COMPANY_B = 200;
    private static final int BRANCH_B = 22;

    @MockitoBean
    private SupplierService supplierService;

    @MockitoBean
    private AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService;

    @Test
    @WithMockUser(username = "ownerA : Owner", roles = {"USER"})
    void ownerA_isDeniedForCompanyB_suppliers() throws Exception {
        // Owner A is NOT a member of Company B: the identity-bound resolver rejects it.
        when(authenticatedEffectiveConfigurationService.resolveTenantContextForAuthenticatedUser(
                eq("ownerA : Owner"), eq(COMPANY_B), eq(BRANCH_B)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED",
                        "User does not belong to the requested tenant"));

        mockMvc.perform(get("/suppliers/all/{companyId}/{branchId}", COMPANY_B, BRANCH_B))
                .andExpect(status().isForbidden());

        // Company B's data path must never be reached.
        verify(supplierService, never()).getSuppliers(eq(COMPANY_B), anyInt());
    }

    @Test
    @WithMockUser(username = "ownerA : Owner", roles = {"USER"})
    void ownerA_canStillReadOwnCompanyA_suppliers() throws Exception {
        ResolvedTenantContext contextA = mock(ResolvedTenantContext.class);
        when(contextA.getTenantId()).thenReturn(COMPANY_A);
        when(contextA.getActiveBranchId()).thenReturn(BRANCH_A);
        when(authenticatedEffectiveConfigurationService.resolveTenantContextForAuthenticatedUser(
                eq("ownerA : Owner"), eq(COMPANY_A), eq(BRANCH_A))).thenReturn(contextA);
        when(authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                eq("ownerA : Owner"), eq(COMPANY_A), eq(BRANCH_A))).thenReturn(new ArrayList<>());
        when(supplierService.getSuppliers(eq(COMPANY_A), eq(BRANCH_A))).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/suppliers/all/{companyId}/{branchId}", COMPANY_A, BRANCH_A))
                .andExpect(status().isOk());

        verify(supplierService).getSuppliers(eq(COMPANY_A), eq(BRANCH_A));
    }
}
