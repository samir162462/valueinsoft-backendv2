package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchEffectiveSettingConfig;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingDefinitionConfig;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingsBundleResponse;
import com.example.valueinsoftbackend.Model.Request.BranchSettings.BranchSettingValueInput;
import com.example.valueinsoftbackend.Model.Request.BranchSettings.BranchSettingsBatchUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BranchSettingsService {

    private final DbBranchSettings dbBranchSettings;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public BranchSettingsService(DbBranchSettings dbBranchSettings,
                                 AuthorizationService authorizationService,
                                 ObjectMapper objectMapper) {
        this.dbBranchSettings = dbBranchSettings;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    public BranchSettingsBundleResponse getEffectiveSettingsForAuthenticatedUser(String authenticatedName,
                                                                                 Integer tenantId,
                                                                                 Integer branchId) {
        int safeTenantId = requirePositive(tenantId, "tenantId");
        int safeBranchId = requirePositive(branchId, "branchId");
        requireBranchScope(safeTenantId, safeBranchId);
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                safeTenantId,
                safeBranchId,
                "company.settings.read"
        );
        return buildBundle(safeTenantId, safeBranchId, "server");
    }

    public BranchSettingsBundleResponse getBranchSettingsForAuthenticatedUser(String authenticatedName,
                                                                              int tenantId,
                                                                              int branchId) {
        requireBranchScope(tenantId, branchId);
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                tenantId,
                branchId,
                "company.settings.read"
        );
        return buildBundle(tenantId, branchId, "server");
    }

    public BranchSettingsBundleResponse saveBranchSettingsForAuthenticatedUser(String authenticatedName,
                                                                               int tenantId,
                                                                               int branchId,
                                                                               BranchSettingsBatchUpdateRequest request) {
        requireBranchScope(tenantId, branchId);
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                tenantId,
                branchId,
                "company.settings.edit"
        );
        saveBranchSettingsInternal(tenantId, branchId, authenticatedName, request);
        return buildBundle(tenantId, branchId, "server");
    }

    protected BranchSettingsBundleResponse buildBundle(int tenantId, int branchId, String source) {
        ArrayList<BranchSettingDefinitionConfig> definitions = dbBranchSettings.getDefinitions(true);
        Map<String, Object> overrides = dbBranchSettings.getActiveOverrideValueMap(tenantId, branchId);
        ArrayList<BranchEffectiveSettingConfig> effectiveSettings = new ArrayList<>();

        definitions.forEach((definition) -> {
            boolean hasOverride = overrides.containsKey(definition.getSettingKey());
            effectiveSettings.add(new BranchEffectiveSettingConfig(
                    definition.getSettingKey(),
                    hasOverride ? overrides.get(definition.getSettingKey()) : definition.getDefaultValue(),
                    hasOverride ? "branch_override" : "default"
            ));
        });

        return new BranchSettingsBundleResponse(
                tenantId,
                branchId,
                definitions,
                effectiveSettings,
                new Timestamp(System.currentTimeMillis()),
                source
        );
    }

    protected void saveBranchSettingsInternal(int tenantId,
                                              int branchId,
                                              String actor,
                                              BranchSettingsBatchUpdateRequest request) {
        ArrayList<BranchSettingDefinitionConfig> definitions = dbBranchSettings.getDefinitions(true);
        Map<String, BranchSettingDefinitionConfig> definitionMap = definitions.stream().collect(
                Collectors.toMap(BranchSettingDefinitionConfig::getSettingKey, definition -> definition, (left, right) -> left, LinkedHashMap::new)
        );

        ArrayList<BranchSettingValueInput> items = request == null || request.getItems() == null
                ? new ArrayList<>()
                : request.getItems();

        for (BranchSettingValueInput item : items) {
            BranchSettingDefinitionConfig definition = definitionMap.get(item.getSettingKey());
            if (definition == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_SETTING_UNKNOWN", "Unknown branch setting key: " + item.getSettingKey());
            }

            Object normalizedValue = normalizeValue(definition, item.getValue());
            if (jsonEquals(normalizedValue, definition.getDefaultValue())) {
                dbBranchSettings.deactivateBranchSettingValue(tenantId, branchId, definition.getSettingKey(), actor);
            } else {
                dbBranchSettings.upsertBranchSettingValue(tenantId, branchId, definition.getSettingKey(), normalizedValue, actor);
            }
        }
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

    private int requirePositive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", fieldName + " is required");
        }
        return value;
    }

    private Object normalizeValue(BranchSettingDefinitionConfig definition, Object value) {
        if (value == null) {
            return null;
        }

        String valueType = definition.getValueType() == null
                ? "string"
                : definition.getValueType().trim().toLowerCase(Locale.ROOT);

        switch (valueType) {
            case "boolean":
                return normalizeBoolean(definition, value);
            case "number":
                return normalizeNumber(definition, value);
            case "enum":
                return normalizeEnum(definition, value);
            case "string":
            default:
                return String.valueOf(value).trim();
        }
    }

    private Boolean normalizeBoolean(BranchSettingDefinitionConfig definition, Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        String normalizedValue = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalizedValue)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalizedValue)) {
            return Boolean.FALSE;
        }

        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "BRANCH_SETTING_INVALID_BOOLEAN",
                "Invalid boolean value for setting " + definition.getSettingKey()
        );
    }

    private Object normalizeNumber(BranchSettingDefinitionConfig definition, Object value) {
        double numericValue;

        if (value instanceof Number) {
            numericValue = ((Number) value).doubleValue();
        } else {
            try {
                numericValue = Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException error) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "BRANCH_SETTING_INVALID_NUMBER",
                        "Invalid numeric value for setting " + definition.getSettingKey()
                );
            }
        }

        if (!Double.isFinite(numericValue)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_SETTING_INVALID_NUMBER",
                    "Invalid numeric value for setting " + definition.getSettingKey()
            );
        }

        Double minValue = readValidationNumber(definition, "minValue");
        Double maxValue = readValidationNumber(definition, "maxValue");

        if (minValue != null && numericValue < minValue) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_SETTING_NUMBER_TOO_SMALL",
                    "Value for setting " + definition.getSettingKey() + " is below the minimum"
            );
        }

        if (maxValue != null && numericValue > maxValue) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_SETTING_NUMBER_TOO_LARGE",
                    "Value for setting " + definition.getSettingKey() + " is above the maximum"
            );
        }

        if (Math.rint(numericValue) == numericValue) {
            return (int) numericValue;
        }

        return numericValue;
    }

    private String normalizeEnum(BranchSettingDefinitionConfig definition, Object value) {
        String normalizedValue = String.valueOf(value).trim();
        Set<String> allowedValues = readAllowedEnumValues(definition);

        if (!allowedValues.contains(normalizedValue)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BRANCH_SETTING_INVALID_ENUM",
                    "Invalid enum value for setting " + definition.getSettingKey()
            );
        }

        return normalizedValue;
    }

    private Double readValidationNumber(BranchSettingDefinitionConfig definition, String key) {
        if (!(definition.getValidation() instanceof Map)) {
            return null;
        }

        Object rawValue = ((Map<?, ?>) definition.getValidation()).get(key);
        if (!(rawValue instanceof Number)) {
            return null;
        }

        return ((Number) rawValue).doubleValue();
    }

    private Set<String> readAllowedEnumValues(BranchSettingDefinitionConfig definition) {
        if (!(definition.getOptions() instanceof List)) {
            return Set.of();
        }

        return ((List<?>) definition.getOptions()).stream()
                .map(option -> {
                    if (option instanceof Map) {
                        Object value = ((Map<?, ?>) option).get("value");
                        return value == null ? null : String.valueOf(value);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean jsonEquals(Object left, Object right) {
        JsonNode leftNode = objectMapper.valueToTree(left);
        JsonNode rightNode = objectMapper.valueToTree(right);
        return Objects.equals(leftNode, rightNode);
    }
}
