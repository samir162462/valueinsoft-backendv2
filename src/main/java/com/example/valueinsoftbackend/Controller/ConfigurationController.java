package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingsBundleResponse;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Model.Configuration.NavigationItemConfig;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import com.example.valueinsoftbackend.Service.AuthenticatedEffectiveConfigurationService;
import com.example.valueinsoftbackend.Service.BranchSettingsService;
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
    private final BranchSettingsService branchSettingsService;

    public ConfigurationController(AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService,
                                   BranchSettingsService branchSettingsService) {
        this.authenticatedEffectiveConfigurationService = authenticatedEffectiveConfigurationService;
        this.branchSettingsService = branchSettingsService;
    }

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

    @GetMapping("/navigation")
    public ArrayList<NavigationItemConfig> getNavigation(Principal principal,
                                                         @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                         @RequestParam(value = "branchId", required = false) Integer branchId) {
        return authenticatedEffectiveConfigurationService.getNavigationForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    @GetMapping("/branch-settings/effective")
    public BranchSettingsBundleResponse getEffectiveBranchSettings(Principal principal,
                                                                  @RequestParam Integer tenantId,
                                                                  @RequestParam Integer branchId) {
        return branchSettingsService.getEffectiveSettingsForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }
}
