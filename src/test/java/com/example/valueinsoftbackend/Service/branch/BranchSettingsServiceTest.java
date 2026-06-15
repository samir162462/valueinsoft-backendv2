package com.example.valueinsoftbackend.Service.branch;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingDefinitionConfig;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingsBundleResponse;
import com.example.valueinsoftbackend.Model.Request.BranchSettings.BranchSettingValueInput;
import com.example.valueinsoftbackend.Model.Request.BranchSettings.BranchSettingsBatchUpdateRequest;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchSettingsServiceTest {
    private DbBranchSettings dbBranchSettings;
    private AuthorizationService authorizationService;
    private BranchSettingsService branchSettingsService;

    @BeforeEach
    void setUp() {
        dbBranchSettings = Mockito.mock(DbBranchSettings.class);
        authorizationService = Mockito.mock(AuthorizationService.class);
        branchSettingsService = new BranchSettingsService(dbBranchSettings, authorizationService, new ObjectMapper());
    }

    @Test
    void getBranchSettingsChecksScopeAndAuthorizationThenBuildsBundle() {
        when(dbBranchSettings.branchBelongsToTenant(eq(1074), eq(1095))).thenReturn(true);
        when(dbBranchSettings.getDefinitions(eq(true))).thenReturn(new ArrayList<>(List.of(
                definition("sales", "receipt_footer", "string", "Thanks", null, null)
        )));
        when(dbBranchSettings.getActiveOverrideValueMap(eq(1074), eq(1095))).thenReturn(Map.of(
                "receipt_footer", "Visit again"
        ));

        BranchSettingsBundleResponse response = branchSettingsService.getBranchSettingsForAuthenticatedUser(
                "owner@example.com",
                1074,
                1095
        );

        assertEquals(1074, response.getTenantId());
        assertEquals(1095, response.getBranchId());
        assertEquals("server", response.getSource());
        assertNotNull(response.getLoadedAt());
        assertEquals(1, response.getEffectiveSettings().size());
        assertEquals("Visit again", response.getEffectiveSettings().get(0).getEffectiveValue());
        assertEquals("branch_override", response.getEffectiveSettings().get(0).getSource());
        verify(authorizationService).assertAuthenticatedCapability(
                "owner@example.com",
                1074,
                1095,
                "company.settings.read"
        );
    }

    @Test
    void getBranchSettingsRejectsBranchOutsideTenantBeforeAuthorization() {
        when(dbBranchSettings.branchBelongsToTenant(eq(1074), eq(9999))).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () ->
                branchSettingsService.getBranchSettingsForAuthenticatedUser("owner@example.com", 1074, 9999));

        assertEquals("BRANCH_SCOPE_INVALID", exception.getCode());
        verify(authorizationService, never()).assertAuthenticatedCapability(
                Mockito.anyString(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyString()
        );
    }

    @Test
    void saveBranchSettingsUpsertsNormalizedValuesAndDeactivatesDefaults() {
        when(dbBranchSettings.branchBelongsToTenant(eq(1074), eq(1095))).thenReturn(true);
        ArrayList<BranchSettingDefinitionConfig> definitions = new ArrayList<>(List.of(
                definition("sales", "allow_returns", "boolean", false, null, null),
                definition("sales", "receipt_copies", "number", 1, null, Map.of("minValue", 1, "maxValue", 5)),
                definition("sales", "tax_mode", "enum", "exclusive", List.of(option("exclusive"), option("inclusive")), null),
                definition("sales", "receipt_footer", "string", "Thanks", null, null)
        ));
        when(dbBranchSettings.getDefinitions(eq(true))).thenReturn(definitions);
        when(dbBranchSettings.getActiveOverrideValueMap(eq(1074), eq(1095))).thenReturn(new LinkedHashMap<>());

        BranchSettingsBatchUpdateRequest request = new BranchSettingsBatchUpdateRequest(new ArrayList<>(List.of(
                new BranchSettingValueInput("allow_returns", "true"),
                new BranchSettingValueInput("receipt_copies", "3"),
                new BranchSettingValueInput("tax_mode", "inclusive"),
                new BranchSettingValueInput("receipt_footer", " Thanks ")
        )));

        branchSettingsService.saveBranchSettingsForAuthenticatedUser("manager@example.com", 1074, 1095, request);

        verify(authorizationService).assertAuthenticatedCapability(
                "manager@example.com",
                1074,
                1095,
                "company.settings.edit"
        );
        verify(dbBranchSettings).upsertBranchSettingValue(1074, 1095, "allow_returns", true, "manager@example.com");
        verify(dbBranchSettings).upsertBranchSettingValue(1074, 1095, "receipt_copies", 3, "manager@example.com");
        verify(dbBranchSettings).upsertBranchSettingValue(1074, 1095, "tax_mode", "inclusive", "manager@example.com");
        verify(dbBranchSettings).deactivateBranchSettingValue(1074, 1095, "receipt_footer", "manager@example.com");
    }

    @Test
    void saveBranchSettingsRejectsUnknownSettingKey() {
        when(dbBranchSettings.branchBelongsToTenant(eq(1074), eq(1095))).thenReturn(true);
        when(dbBranchSettings.getDefinitions(eq(true))).thenReturn(new ArrayList<>(List.of(
                definition("sales", "allow_returns", "boolean", false, null, null)
        )));
        BranchSettingsBatchUpdateRequest request = new BranchSettingsBatchUpdateRequest(new ArrayList<>(List.of(
                new BranchSettingValueInput("unknown_key", true)
        )));

        ApiException exception = assertThrows(ApiException.class, () ->
                branchSettingsService.saveBranchSettingsForAuthenticatedUser("manager@example.com", 1074, 1095, request));

        assertEquals("BRANCH_SETTING_UNKNOWN", exception.getCode());
    }

    @Test
    void saveBranchSettingsRejectsOutOfRangeNumber() {
        when(dbBranchSettings.branchBelongsToTenant(eq(1074), eq(1095))).thenReturn(true);
        when(dbBranchSettings.getDefinitions(eq(true))).thenReturn(new ArrayList<>(List.of(
                definition("sales", "receipt_copies", "number", 1, null, Map.of("minValue", 1, "maxValue", 5))
        )));
        BranchSettingsBatchUpdateRequest request = new BranchSettingsBatchUpdateRequest(new ArrayList<>(List.of(
                new BranchSettingValueInput("receipt_copies", 7)
        )));

        ApiException exception = assertThrows(ApiException.class, () ->
                branchSettingsService.saveBranchSettingsForAuthenticatedUser("manager@example.com", 1074, 1095, request));

        assertEquals("BRANCH_SETTING_NUMBER_TOO_LARGE", exception.getCode());
    }

    private BranchSettingDefinitionConfig definition(String groupKey,
                                                     String settingKey,
                                                     String valueType,
                                                     Object defaultValue,
                                                     Object options,
                                                     Object validation) {
        return new BranchSettingDefinitionConfig(
                groupKey,
                settingKey,
                settingKey,
                settingKey,
                valueType,
                valueType,
                defaultValue,
                options,
                validation,
                true,
                1
        );
    }

    private Map<String, String> option(String value) {
        return Map.of("value", value, "label", value);
    }
}
