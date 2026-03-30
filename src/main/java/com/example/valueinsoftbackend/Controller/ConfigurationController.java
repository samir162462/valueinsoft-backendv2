package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import com.example.valueinsoftbackend.Service.AuthenticatedEffectiveConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;

/**
 * Exposes authenticated configuration endpoints used by the frontend shell.
 */
@RestController
@RequestMapping("/api/config/me")
public class ConfigurationController {

    private final AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService;

    public ConfigurationController(AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService) {
        this.authenticatedEffectiveConfigurationService = authenticatedEffectiveConfigurationService;
    }

    /**
     * Returns the effective configuration payload for the authenticated user.
     *
     * @param principal current authenticated principal derived from the JWT filter
     * @param tenantId optional tenant override when the user can access more than one tenant
     * @param branchId optional active branch override inside the resolved tenant
     * @return resolved tenant configuration, module state, workflow flags, assignments, grants, and overrides
     */
    @GetMapping("/effective")
    public EffectiveConfiguration getEffectiveConfiguration(Principal principal,
                                                            @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                            @RequestParam(value = "branchId", required = false) Integer branchId) {
        return authenticatedEffectiveConfigurationService.getEffectiveConfigurationForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    /**
     * Returns only the effective capabilities for the authenticated user in the resolved tenant context.
     */
    @GetMapping("/capabilities")
    public ArrayList<ResolvedCapabilityConfig> getEffectiveCapabilities(Principal principal,
                                                                        @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                                        @RequestParam(value = "branchId", required = false) Integer branchId) {
        return authenticatedEffectiveConfigurationService.getEffectiveCapabilitiesForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }
}
