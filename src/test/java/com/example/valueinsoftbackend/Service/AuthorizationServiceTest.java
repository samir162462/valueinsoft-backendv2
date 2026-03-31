package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {

    private AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService;
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authenticatedEffectiveConfigurationService = Mockito.mock(AuthenticatedEffectiveConfigurationService.class);
        authorizationService = new AuthorizationService(authenticatedEffectiveConfigurationService);
    }

    @Test
    void assertAuthenticatedCapabilityAllowsResolvedGrant() {
        ArrayList<ResolvedCapabilityConfig> capabilities = new ArrayList<>();
        capabilities.add(new ResolvedCapabilityConfig(
                "users.account.create",
                "allow",
                "company",
                null,
                "role_grant",
                "Owner",
                1L,
                null
        ));

        when(authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                eq("sam : Owner"),
                eq(7),
                eq(3)
        )).thenReturn(capabilities);

        assertDoesNotThrow(() ->
                authorizationService.assertAuthenticatedCapability("sam : Owner", 7, 3, "users.account.create"));
    }

    @Test
    void assertAuthenticatedCapabilityRejectsMissingGrant() {
        when(authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                eq("sam"),
                eq(7),
                eq(3)
        )).thenReturn(new ArrayList<>());

        ApiException exception = assertThrows(ApiException.class, () ->
                authorizationService.assertAuthenticatedCapability("sam", 7, 3, "users.account.create"));

        assertEquals("CAPABILITY_DENIED", exception.getCode());
    }

    @Test
    void assertAuthenticatedCapabilityRejectsNonAllowGrantMode() {
        ArrayList<ResolvedCapabilityConfig> capabilities = new ArrayList<>();
        capabilities.add(new ResolvedCapabilityConfig(
                "users.account.create",
                "deny",
                "company",
                null,
                "user_override",
                null,
                null,
                9L
        ));

        when(authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                eq("sam"),
                eq(7),
                eq(3)
        )).thenReturn(capabilities);

        ApiException exception = assertThrows(ApiException.class, () ->
                authorizationService.assertAuthenticatedCapability("sam", 7, 3, "users.account.create"));

        assertEquals("CAPABILITY_DENIED", exception.getCode());
    }

    @Test
    void assertSelfCapabilityAllowsMatchingLegacyPrincipalName() {
        ArrayList<ResolvedCapabilityConfig> capabilities = new ArrayList<>();
        capabilities.add(new ResolvedCapabilityConfig(
                "profile.self.read",
                "allow",
                "self",
                null,
                "role_grant",
                "Cashier",
                2L,
                null
        ));

        when(authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                eq("sam : Cashier"),
                eq(null),
                eq(null)
        )).thenReturn(capabilities);

        assertDoesNotThrow(() ->
                authorizationService.assertSelfCapability("sam : Cashier", "sam", "profile.self.read"));
    }

    @Test
    void assertSelfCapabilityRejectsCrossUserAccess() {
        ApiException exception = assertThrows(ApiException.class, () ->
                authorizationService.assertSelfCapability("sam : Owner", "other-user", "profile.self.read"));

        assertEquals("SELF_ACCESS_DENIED", exception.getCode());
    }
}
