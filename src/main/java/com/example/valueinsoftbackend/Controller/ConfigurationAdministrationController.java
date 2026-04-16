package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Configuration.TenantAdminPortalConfig;
import com.example.valueinsoftbackend.Model.Request.Configuration.SaveRoleGrantRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.SaveTenantRoleAssignmentRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.SaveUserGrantOverrideRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.UpdateTenantModuleOverrideRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.UpdateTenantWorkflowOverrideRequest;
import com.example.valueinsoftbackend.Service.ConfigurationAdministrationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/config/admin")
public class ConfigurationAdministrationController {

    private final ConfigurationAdministrationService configurationAdministrationService;

    public ConfigurationAdministrationController(ConfigurationAdministrationService configurationAdministrationService) {
        this.configurationAdministrationService = configurationAdministrationService;
    }

    @GetMapping("/portal")
    public TenantAdminPortalConfig getPortal(Principal principal,
                                             @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                             @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.getPortalForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId
        );
    }

    @PutMapping("/modules/{moduleId}")
    public TenantAdminPortalConfig updateModuleOverride(Principal principal,
                                                        @PathVariable String moduleId,
                                                        @Valid @RequestBody UpdateTenantModuleOverrideRequest request,
                                                        @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                        @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.updateModuleOverrideForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                moduleId,
                request
        );
    }

    @PutMapping("/workflows/{flagKey}")
    public TenantAdminPortalConfig updateWorkflowOverride(Principal principal,
                                                          @PathVariable String flagKey,
                                                          @Valid @RequestBody UpdateTenantWorkflowOverrideRequest request,
                                                          @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                          @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.updateWorkflowOverrideForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                flagKey,
                request
        );
    }

    @PostMapping("/assignments")
    public TenantAdminPortalConfig saveRoleAssignment(Principal principal,
                                                      @Valid @RequestBody SaveTenantRoleAssignmentRequest request,
                                                      @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                      @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.saveRoleAssignmentForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                request
        );
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public TenantAdminPortalConfig deactivateRoleAssignment(Principal principal,
                                                            @PathVariable long assignmentId,
                                                            @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                            @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.deactivateRoleAssignmentForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                assignmentId
        );
    }

    @PutMapping("/roles/{roleId}/grants")
    public TenantAdminPortalConfig saveRoleGrant(Principal principal,
                                                 @PathVariable String roleId,
                                                 @Valid @RequestBody SaveRoleGrantRequest request,
                                                 @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                 @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.saveRoleGrantForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                roleId,
                request
        );
    }

    @DeleteMapping("/roles/{roleId}/grants")
    public TenantAdminPortalConfig deleteRoleGrant(Principal principal,
                                                   @PathVariable String roleId,
                                                   @RequestParam String capabilityKey,
                                                   @RequestParam String scopeType,
                                                   @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                   @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.removeRoleGrantForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                roleId,
                capabilityKey,
                scopeType
        );
    }

    @PutMapping("/users/{userId}/overrides")
    public TenantAdminPortalConfig saveUserGrantOverride(Principal principal,
                                                         @PathVariable int userId,
                                                         @Valid @RequestBody SaveUserGrantOverrideRequest request,
                                                         @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                         @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.saveUserGrantOverrideForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                userId,
                request
        );
    }

    @DeleteMapping("/users/{userId}/overrides")
    public TenantAdminPortalConfig deleteUserGrantOverride(Principal principal,
                                                           @PathVariable int userId,
                                                           @RequestParam String capabilityKey,
                                                           @RequestParam String scopeType,
                                                           @RequestParam(value = "scopeBranchId", required = false) Integer scopeBranchId,
                                                           @RequestParam(value = "tenantId", required = false) Integer tenantId,
                                                           @RequestParam(value = "branchId", required = false) Integer branchId) {
        return configurationAdministrationService.removeUserGrantOverrideForAuthenticatedUser(
                principal.getName(),
                tenantId,
                branchId,
                userId,
                capabilityKey,
                scopeType,
                scopeBranchId
        );
    }
}
