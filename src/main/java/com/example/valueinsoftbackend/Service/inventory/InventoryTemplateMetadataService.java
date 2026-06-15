package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryTemplateMetadataService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformAuthorizationService platformAuthorizationService;

    public InventoryTemplateMetadataService(JdbcTemplate jdbcTemplate,
                                            ObjectMapper objectMapper,
                                            PlatformAuthorizationService platformAuthorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.objectMapper = objectMapper;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    public ArrayList<Map<String, Object>> getTemplates(int companyId) {
        return getTemplatesInternal(companyId, false);
    }

    public ArrayList<Map<String, Object>> getAdminTemplates(String authenticatedName, int companyId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.read");
        return getTemplatesInternal(companyId, true);
    }

    @Transactional
    public Map<String, Object> updateTemplate(String authenticatedName,
                                              int companyId,
                                              String templateKey,
                                              Map<String, Object> request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.write");
        String normalizedTemplateKey = normalizeRequired(templateKey, "templateKey").toLowerCase();
        String templateTable = TenantSqlIdentifiers.inventoryProductTemplateTable(companyId);
        String attributeTable = TenantSqlIdentifiers.inventoryAttributeDefinitionTable(companyId);
        String bindingTable = TenantSqlIdentifiers.inventoryTemplateAttributeTable(companyId);

        String businessLineKey = normalizeBusinessLineKey(stringValue(request.get("businessLineKey")));
        MapSqlParameterSource templateParams = new MapSqlParameterSource()
                .addValue("templateKey", normalizedTemplateKey)
                .addValue("businessLineKey", businessLineKey)
                .addValue("displayName", normalizeRequired(stringValue(request.get("displayName")), "displayName"))
                .addValue("majorKey", normalizeNullable(stringValue(request.get("majorKey"))))
                .addValue("supportsSerial", booleanValue(request.get("supportsSerial")))
                .addValue("supportsBatch", booleanValue(request.get("supportsBatch")))
                .addValue("supportsExpiry", booleanValue(request.get("supportsExpiry")))
                .addValue("supportsWeight", booleanValue(request.get("supportsWeight")))
                .addValue("isActive", request.get("isActive") == null || booleanValue(request.get("isActive")));

        namedParameterJdbcTemplate.update(
                """
                INSERT INTO %s (
                    business_line_key, template_key, display_name, major_key,
                    supports_serial, supports_batch, supports_expiry, supports_weight, is_system, is_active, updated_at
                ) VALUES (
                    :businessLineKey, :templateKey, :displayName, :majorKey,
                    :supportsSerial, :supportsBatch, :supportsExpiry, :supportsWeight, FALSE, :isActive, CURRENT_TIMESTAMP
                )
                ON CONFLICT (template_key) DO UPDATE
                SET business_line_key = EXCLUDED.business_line_key,
                    display_name = EXCLUDED.display_name,
                    major_key = EXCLUDED.major_key,
                    supports_serial = EXCLUDED.supports_serial,
                    supports_batch = EXCLUDED.supports_batch,
                    supports_expiry = EXCLUDED.supports_expiry,
                    supports_weight = EXCLUDED.supports_weight,
                    is_active = EXCLUDED.is_active,
                    updated_at = CURRENT_TIMESTAMP
                """.formatted(templateTable),
                templateParams
        );

        Long templateId = jdbcTemplate.queryForObject(
                "SELECT template_id FROM " + templateTable + " WHERE template_key = ?",
                Long.class,
                normalizedTemplateKey
        );

        if (templateId == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_UPSERT_FAILED", "Unable to resolve saved inventory template");
        }

        jdbcTemplate.update("DELETE FROM " + bindingTable + " WHERE template_id = ?", templateId);

        List<Map<String, Object>> attributes = normalizeAttributeList(request.get("attributes"));
        Map<String, Long> attributeIds = new HashMap<>();

        for (Map<String, Object> attribute : attributes) {
            String attributeKey = normalizeRequired(stringValue(attribute.get("attributeKey")), "attributeKey");
            MapSqlParameterSource attributeParams = new MapSqlParameterSource()
                    .addValue("businessLineKey", businessLineKey)
                    .addValue("attributeKey", attributeKey)
                    .addValue("displayName", normalizeRequired(stringValue(attribute.get("displayName")), "attribute.displayName"))
                    .addValue("dataType", normalizeDataType(stringValue(attribute.get("dataType"))))
                    .addValue("required", booleanValue(attribute.get("required")))
                    .addValue("filterable", booleanValue(attribute.get("filterable")))
                    .addValue("searchable", booleanValue(attribute.get("searchable")))
                    .addValue("fieldSchema", toJson(attribute.get("fieldSchema")));

            namedParameterJdbcTemplate.update(
                    """
                    INSERT INTO %s (
                        business_line_key, attribute_key, display_name, data_type,
                        is_required, is_filterable, is_searchable, field_schema, updated_at
                    ) VALUES (
                        :businessLineKey, :attributeKey, :displayName, :dataType,
                        :required, :filterable, :searchable, CAST(:fieldSchema AS jsonb), CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (business_line_key, attribute_key) DO UPDATE
                    SET display_name = EXCLUDED.display_name,
                        data_type = EXCLUDED.data_type,
                        is_required = EXCLUDED.is_required,
                        is_filterable = EXCLUDED.is_filterable,
                        is_searchable = EXCLUDED.is_searchable,
                        field_schema = EXCLUDED.field_schema,
                        updated_at = CURRENT_TIMESTAMP
                    """.formatted(attributeTable),
                    attributeParams
            );

            Long attributeId = jdbcTemplate.queryForObject(
                    "SELECT attribute_id FROM " + attributeTable + " WHERE business_line_key = ? AND attribute_key = ?",
                    Long.class,
                    businessLineKey,
                    attributeKey
            );

            if (attributeId != null) {
                attributeIds.put(attributeKey, attributeId);
            }
        }

        for (int index = 0; index < attributes.size(); index++) {
            Map<String, Object> attribute = attributes.get(index);
            String attributeKey = normalizeRequired(stringValue(attribute.get("attributeKey")), "attributeKey");
            Long attributeId = attributeIds.get(attributeKey);
            if (attributeId == null) {
                continue;
            }

            namedParameterJdbcTemplate.update(
                    """
                    INSERT INTO %s (
                        template_id, attribute_id, display_order, is_required, group_key, default_value_jsonb
                    ) VALUES (
                        :templateId, :attributeId, :displayOrder, :required, :groupKey, CAST(:defaultValue AS jsonb)
                    )
                    """.formatted(bindingTable),
                    new MapSqlParameterSource()
                            .addValue("templateId", templateId)
                            .addValue("attributeId", attributeId)
                            .addValue("displayOrder", intValue(attribute.get("displayOrder")) == 0 ? index : intValue(attribute.get("displayOrder")))
                            .addValue("required", booleanValue(attribute.get("required")))
                            .addValue("groupKey", normalizeNullable(stringValue(attribute.get("groupKey"))))
                            .addValue("defaultValue", toJson(attribute.get("defaultValue")))
            );
        }

        return getAdminTemplates(authenticatedName, companyId).stream()
                .filter(template -> normalizedTemplateKey.equals(String.valueOf(template.get("templateKey"))))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_RELOAD_FAILED", "Unable to reload saved inventory template"));
    }

    private ArrayList<Map<String, Object>> getTemplatesInternal(int companyId, boolean includeInactive) {
        String sql = """
                SELECT
                    template.template_key AS template_key,
                    template.display_name AS template_display_name,
                    template.business_line_key AS business_line_key,
                    template.major_key AS major_key,
                    template.supports_serial AS supports_serial,
                    template.supports_batch AS supports_batch,
                    template.supports_expiry AS supports_expiry,
                    template.supports_weight AS supports_weight,
                    template.is_active AS is_active,
                    attribute.attribute_key AS attribute_key,
                    attribute.display_name AS attribute_display_name,
                    attribute.data_type AS data_type,
                    attribute.is_required AS attribute_required,
                    attribute.is_filterable AS is_filterable,
                    attribute.is_searchable AS is_searchable,
                    attribute.field_schema AS field_schema,
                    binding.display_order AS display_order,
                    binding.is_required AS binding_required,
                    binding.group_key AS group_key,
                    binding.default_value_jsonb AS default_value_jsonb
                FROM %s template
                LEFT JOIN %s binding
                  ON binding.template_id = template.template_id
                LEFT JOIN %s attribute
                  ON attribute.attribute_id = binding.attribute_id
                WHERE (:includeInactive = TRUE OR template.is_active = TRUE)
                ORDER BY template.display_name ASC, binding.display_order ASC, attribute.display_name ASC
                """.formatted(
                TenantSqlIdentifiers.inventoryProductTemplateTable(companyId),
                TenantSqlIdentifiers.inventoryTemplateAttributeTable(companyId),
                TenantSqlIdentifiers.inventoryAttributeDefinitionTable(companyId)
        );

        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource().addValue("includeInactive", includeInactive)
        );
        LinkedHashMap<String, Map<String, Object>> templates = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String templateKey = stringValue(row.get("template_key"));
            Map<String, Object> template = templates.computeIfAbsent(templateKey, key -> createTemplateMap(row));
            if (row.get("attribute_key") != null) {
                @SuppressWarnings("unchecked")
                ArrayList<Map<String, Object>> attributes = (ArrayList<Map<String, Object>>) template.get("attributes");
                attributes.add(createAttributeMap(row));
            }
        }

        return new ArrayList<>(templates.values());
    }

    private Map<String, Object> createTemplateMap(Map<String, Object> row) {
        LinkedHashMap<String, Object> template = new LinkedHashMap<>();
        String businessLineKey = stringValue(row.get("business_line_key"));
        template.put("templateKey", stringValue(row.get("template_key")));
        template.put("displayName", stringValue(row.get("template_display_name")));
        template.put("businessLineKey", businessLineKey);
        template.put("majorKey", stringValue(row.get("major_key")));
        template.put("supportsSerial", booleanValue(row.get("supports_serial")));
        template.put("supportsBatch", booleanValue(row.get("supports_batch")));
        template.put("supportsExpiry", booleanValue(row.get("supports_expiry")));
        template.put("supportsWeight", booleanValue(row.get("supports_weight")));
        template.put("isActive", booleanValue(row.get("is_active")));
        template.put("baseUomCode", defaultBaseUomCode(businessLineKey));
        template.put("pricingPolicyCode", defaultPricingPolicyCode(businessLineKey));
        template.put("attributes", new ArrayList<Map<String, Object>>());
        return template;
    }

    private Map<String, Object> createAttributeMap(Map<String, Object> row) {
        LinkedHashMap<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("attributeKey", stringValue(row.get("attribute_key")));
        attribute.put("displayName", stringValue(row.get("attribute_display_name")));
        attribute.put("dataType", stringValue(row.get("data_type")));
        attribute.put("required", booleanValue(row.get("attribute_required")) || booleanValue(row.get("binding_required")));
        attribute.put("filterable", booleanValue(row.get("is_filterable")));
        attribute.put("searchable", booleanValue(row.get("is_searchable")));
        attribute.put("groupKey", stringValue(row.get("group_key")));
        attribute.put("displayOrder", intValue(row.get("display_order")));
        attribute.put("fieldSchema", parseJsonObject(row.get("field_schema")));
        attribute.put("defaultValue", parseJsonValue(row.get("default_value_jsonb")));
        return attribute;
    }

    private Object parseJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> parseJsonObject(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String defaultBaseUomCode(String businessLineKey) {
        return switch (businessLineKey) {
            case "GOLD" -> "GRAM";
            case "CHEMICAL" -> "LITER";
            default -> "PCS";
        };
    }

    private String defaultPricingPolicyCode(String businessLineKey) {
        return switch (businessLineKey) {
            case "GOLD" -> "WEIGHT_MARKET";
            case "CHEMICAL" -> "FORMULA";
            default -> "FIXED_RETAIL";
        };
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool
                ? bool
                : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_TEMPLATE_FIELD_REQUIRED", fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeBusinessLineKey(String businessLineKey) {
        String normalized = normalizeRequired(businessLineKey, "businessLineKey").toUpperCase().replace('-', '_').replace(' ', '_');
        if (!List.of("MOBILE", "GOLD", "CHEMICAL").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_TEMPLATE_BUSINESS_LINE_INVALID", "Unsupported business line key");
        }
        return normalized;
    }

    private String normalizeDataType(String dataType) {
        String normalized = normalizeRequired(dataType, "dataType").toUpperCase();
        if (!List.of("TEXT", "NUMBER", "BOOLEAN", "DATE", "JSON").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_TEMPLATE_DATA_TYPE_INVALID", "Unsupported attribute data type");
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeAttributeList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }

        ArrayList<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> mapValue) {
                normalized.add((Map<String, Object>) mapValue);
            }
        }
        return normalized;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_TEMPLATE_JSON_INVALID", "Invalid inventory template JSON payload");
        }
    }
}
