package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingDefinitionConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DbBranchSettings {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    public DbBranchSettings(JdbcTemplate jdbcTemplate,
                            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean branchBelongsToTenant(int tenantId, int branchId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.\"Branch\" WHERE \"branchId\" = ? AND \"companyId\" = ?",
                Integer.class,
                branchId,
                tenantId
        );
        return count != null && count > 0;
    }

    public ArrayList<BranchSettingDefinitionConfig> getDefinitions(boolean activeOnly) {
        String sql = "SELECT group_key, setting_key, display_name, description, value_type, field_type, " +
                "default_value_json::text AS default_value_json, options_json::text AS options_json, " +
                "validation_json::text AS validation_json, active, sort_order " +
                "FROM public.branch_setting_definitions " +
                (activeOnly ? "WHERE active = TRUE " : "") +
                "ORDER BY sort_order ASC, setting_key ASC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new BranchSettingDefinitionConfig(
                rs.getString("group_key"),
                rs.getString("setting_key"),
                rs.getString("display_name"),
                rs.getString("description"),
                rs.getString("value_type"),
                rs.getString("field_type"),
                parseJson(rs.getString("default_value_json")),
                parseJson(rs.getString("options_json")),
                parseJson(rs.getString("validation_json")),
                rs.getBoolean("active"),
                rs.getInt("sort_order")
        )));
    }

    public Map<String, Object> getActiveOverrideValueMap(int tenantId, int branchId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT setting_key, value_json::text AS value_json " +
                        "FROM public.branch_setting_values " +
                        "WHERE tenant_id = ? AND branch_id = ? AND active = TRUE",
                tenantId,
                branchId
        );

        Map<String, Object> values = new LinkedHashMap<>();
        rows.forEach((row) -> values.put(
                stringValue(row.get("setting_key")),
                parseJson(stringValue(row.get("value_json")))
        ));
        return values;
    }

    public void upsertBranchSettingValue(int tenantId,
                                         int branchId,
                                         String settingKey,
                                         Object value,
                                         String actor) {
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.branch_setting_values " +
                        "(tenant_id, branch_id, setting_key, value_json, active, created_by, updated_by) " +
                        "VALUES (:tenantId, :branchId, :settingKey, CAST(:valueJson AS jsonb), TRUE, :actor, :actor) " +
                        "ON CONFLICT (branch_id, setting_key) DO UPDATE SET " +
                        "tenant_id = EXCLUDED.tenant_id, " +
                        "value_json = EXCLUDED.value_json, " +
                        "active = TRUE, " +
                        "updated_by = EXCLUDED.updated_by, " +
                        "updated_at = NOW()",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("branchId", branchId)
                        .addValue("settingKey", settingKey)
                        .addValue("valueJson", toJson(value))
                        .addValue("actor", actor)
        );
    }

    public void deactivateBranchSettingValue(int tenantId,
                                             int branchId,
                                             String settingKey,
                                             String actor) {
        namedParameterJdbcTemplate.update(
                "UPDATE public.branch_setting_values " +
                        "SET active = FALSE, updated_by = :actor, updated_at = NOW() " +
                        "WHERE tenant_id = :tenantId AND branch_id = :branchId AND setting_key = :settingKey",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("branchId", branchId)
                        .addValue("settingKey", settingKey)
                        .addValue("actor", actor)
        );
    }

    private Object parseJson(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(rawValue, Object.class);
        } catch (JsonProcessingException error) {
            return rawValue;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize branch setting value", error);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
