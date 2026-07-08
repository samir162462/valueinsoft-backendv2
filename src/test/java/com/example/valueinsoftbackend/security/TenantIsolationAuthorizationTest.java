package com.example.valueinsoftbackend.security;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1 regression suite for the Owner cross-tenant access bypass in
 * {@link AuthorizationService#hasAuthenticatedCapability}.
 *
 * <p>Fix under test: the owner grant is now preceded by a tenant/branch membership
 * resolution ({@link AuthenticatedEffectiveConfigurationService}). The config service
 * throws TENANT_ACCESS_DENIED / BRANCH_ACCESS_DENIED for a company/branch the user does
 * not belong to. Owners retain broad access ONLY inside a tenant they belong to.</p>
 *
 * <p>Modelling: Owner A belongs to Company A=100 / Branch A=11. Company B=200 / Branch
 * B=22 belong to Owner B. The config service mock reproduces what the real resolver does:
 * throws for a foreign company/branch, returns a grant list for the owner's own tenant.</p>
 */
class TenantIsolationAuthorizationTest {

    private static final String OWNER_A = "ownerA : Owner";
    private static final String EMPLOYEE_A = "employeeA : Cashier";
    private static final int COMPANY_A = 100;
    private static final int COMPANY_B = 200;
    private static final int BRANCH_A = 11;
    private static final int BRANCH_B = 22;
    private static final String CAPABILITY = "inventory.item.read";

    private AuthenticatedEffectiveConfigurationService configService;
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        configService = Mockito.mock(AuthenticatedEffectiveConfigurationService.class);
        authorizationService = new AuthorizationService(configService);
    }

    private ApiException tenantDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED",
                "User does not belong to the requested tenant");
    }

    private ApiException branchDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED",
                "Branch does not belong to the resolved tenant");
    }

    private ArrayList<ResolvedCapabilityConfig> allow(String capabilityKey) {
        ArrayList<ResolvedCapabilityConfig> capabilities = new ArrayList<>();
        capabilities.add(new ResolvedCapabilityConfig(
                capabilityKey, "allow", "company", null, "role_grant", "Owner", 1L, null));
        return capabilities;
    }

    // ---- Owner cross-tenant is denied (the P0) --------------------------------

    @Test
    void ownerA_isDeniedForForeignCompanyB() {
        when(configService.getEffectiveCapabilitiesForAuthenticatedUser(eq(OWNER_A), eq(COMPANY_B), eq(BRANCH_B)))
                .thenThrow(tenantDenied());

        ApiException ex = assertThrows(ApiException.class, () ->
                authorizationService.assertAuthenticatedCapability(OWNER_A, COMPANY_B, BRANCH_B, CAPABILITY));
        assertEquals("TENANT_ACCESS_DENIED", ex.getCode());

        // The membership guard MUST have been consulted (owner path no longer skips it).
        verify(configService).getEffectiveCapabilitiesForAuthenticatedUser(OWNER_A, COMPANY_B, BRANCH_B);
    }

    @Test
    void ownerA_isDeniedForBranchOutsideOwnCompany() {
        // Owner A belongs to Company A, but Branch B does not belong to Company A.
        when(configService.getEffectiveCapabilitiesForAuthenticatedUser(eq(OWNER_A), eq(COMPANY_A), eq(BRANCH_B)))
                .thenThrow(branchDenied());

        ApiException ex = assertThrows(ApiException.class, () ->
                authorizationService.assertAuthenticatedCapability(OWNER_A, COMPANY_A, BRANCH_B, CAPABILITY));
        assertEquals("BRANCH_ACCESS_DENIED", ex.getCode());
    }

    // ---- Owner in-tenant access is preserved ----------------------------------

    @Test
    void ownerA_isStillAllowedForOwnCompanyA() {
        when(configService.getEffectiveCapabilitiesForAuthenticatedUser(eq(OWNER_A), eq(COMPANY_A), eq(BRANCH_A)))
                .thenReturn(allow(CAPABILITY));

        assertDoesNotThrow(() -> authorizationService.assertAuthenticatedCapability(
                OWNER_A, COMPANY_A, BRANCH_A, CAPABILITY),
                "Owners must retain full access inside their own tenant");
    }

    @Test
    void ownerA_isAllowedForOwnCompanyEvenWithoutAnExplicitGrant() {
        // Membership resolves (empty grant list); owner short-circuit still allows in-tenant.
        when(configService.getEffectiveCapabilitiesForAuthenticatedUser(eq(OWNER_A), eq(COMPANY_A), eq(BRANCH_A)))
                .thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> authorizationService.assertAuthenticatedCapability(
                OWNER_A, COMPANY_A, BRANCH_A, "supplier.purchase.create"));
        verify(configService).getEffectiveCapabilitiesForAuthenticatedUser(OWNER_A, COMPANY_A, BRANCH_A);
    }

    // ---- Non-owner control cases (unchanged behaviour) ------------------------

    @Test
    void nonOwner_isDeniedForForeignCompanyB() {
        when(configService.getEffectiveCapabilitiesForAuthenticatedUser(eq(EMPLOYEE_A), eq(COMPANY_B), eq(BRANCH_B)))
                .thenThrow(tenantDenied());

        ApiException ex = assertThrows(ApiException.class, () ->
                authorizationService.assertAuthenticatedCapability(EMPLOYEE_A, COMPANY_B, BRANCH_B, CAPABILITY));
        assertEquals("TENANT_ACCESS_DENIED", ex.getCode());
    }

    @Test
    void nonOwner_withGrant_isAllowedInOwnCompany() {
        when(configService.getEffectiveCapabilitiesForAuthenticatedUser(eq(EMPLOYEE_A), eq(COMPANY_A), eq(BRANCH_A)))
                .thenReturn(allow(CAPABILITY));

        assertDoesNotThrow(() -> authorizationService.assertAuthenticatedCapability(
                EMPLOYEE_A, COMPANY_A, BRANCH_A, CAPABILITY));
    }

    @Test
    void nonOwner_withoutGrant_isDeniedInOwnCompany() {
        when(configService.getEffectiveCapabilitiesForAuthenticatedUser(eq(EMPLOYEE_A), eq(COMPANY_A), eq(BRANCH_A)))
                .thenReturn(new ArrayList<>());

        ApiException ex = assertThrows(ApiException.class, () ->
                authorizationService.assertAuthenticatedCapability(EMPLOYEE_A, COMPANY_A, BRANCH_A, CAPABILITY));
        assertEquals("CAPABILITY_DENIED", ex.getCode());
    }
}
