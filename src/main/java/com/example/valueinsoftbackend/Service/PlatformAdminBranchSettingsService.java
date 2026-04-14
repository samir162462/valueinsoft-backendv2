package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingDefinitionConfig;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingsBundleResponse;
import com.example.valueinsoftbackend.Model.Request.BranchSettings.BranchSettingsBatchUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class PlatformAdminBranchSettingsService {

    private final DbBranchSettings dbBranchSettings;
    private final BranchSettingsService branchSettingsService;
    private final PlatformAuthorizationService platformAuthorizationService;

    public PlatformAdminBranchSettingsService(DbBranchSettings dbBranchSettings,
                                              BranchSettingsService branchSettingsService,
                                              PlatformAuthorizationService platformAuthorizationService) {
        this.dbBranchSettings = dbBranchSettings;
        this.branchSettingsService = branchSettingsService;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    public ArrayList<BranchSettingDefinitionConfig> getDefinitionsForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.read");
        return dbBranchSettings.getDefinitions(true);
    }

    public BranchSettingsBundleResponse getBranchSettingsForAuthenticatedUser(String authenticatedName,
                                                                              int tenantId,
                                                                              int branchId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.read");
        requireBranchScope(tenantId, branchId);
        return branchSettingsService.buildBundle(tenantId, branchId, "server");
    }

    public BranchSettingsBundleResponse saveBranchSettingsForAuthenticatedUser(String authenticatedName,
                                                                               int tenantId,
                                                                               int branchId,
                                                                               BranchSettingsBatchUpdateRequest request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.write");
        requireBranchScope(tenantId, branchId);
        branchSettingsService.saveBranchSettingsInternal(tenantId, branchId, authenticatedName, request);
        return branchSettingsService.buildBundle(tenantId, branchId, "server");
    }

    private void requireBranchScope(int tenantId, int branchId) {
        if (!dbBranchSettings.branchBelongsToTenant(tenantId, branchId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_SCOPE_INVALID",
                    "Branch does not belong to the requested tenant"
            );
        }
    }
}
